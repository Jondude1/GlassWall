package com.jondude1.glasswall.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.jondude1.glasswall.data.Http
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
)

/** Checks the public GitHub repo's latest release and installs its APK. */
object Updater {
    private const val LATEST = "https://api.github.com/repos/Jondude1/GlassWall/releases/latest"

    /** Latest published release, or null if it has no APK or no readable version. */
    suspend fun fetchLatest(): UpdateInfo? = withContext(Dispatchers.IO) {
        val body = Http.get(LATEST, mapOf("Accept" to "application/vnd.github+json"))
        val j = JSONObject(body)
        val tag = j.getString("tag_name")
        val code = Regex("(\\d+)$").find(tag)?.value?.toIntOrNull() ?: return@withContext null
        val assets = j.optJSONArray("assets") ?: return@withContext null
        var apk: String? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk")) {
                apk = a.getString("browser_download_url")
                break
            }
        }
        if (apk == null) return@withContext null
        UpdateInfo(code, tag.removePrefix("v"), apk)
    }

    suspend fun download(context: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "updates")
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "GlassWall-${info.versionName}.apk")
            val c = Http.open(info.apkUrl)
            try {
                val total = c.contentLengthLong
                c.inputStream.use { input ->
                    out.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            output.write(buf, 0, n)
                            read += n
                            if (total > 0) onProgress(read.toFloat() / total)
                        }
                    }
                }
            } finally {
                c.disconnect()
            }
            out
        }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Opens the system screen where the person lets GlassWall install updates. */
    fun openInstallPermission(context: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
