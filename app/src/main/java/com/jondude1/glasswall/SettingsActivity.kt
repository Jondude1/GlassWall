package com.jondude1.glasswall

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jondude1.glasswall.data.Prefs
import com.jondude1.glasswall.sim.Llm
import com.jondude1.glasswall.update.UpdateInfo
import com.jondude1.glasswall.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = Village.prefs(this)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF7FB8FF), background = Color(0xFF070A14), surface = Color(0xFF070A14),
                )
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Settings(prefs)
                    }
                }
            }
        }
    }

    private fun openPicker() {
        val component = ComponentName(this, VillageWallpaperService::class.java)
        try {
            startActivity(
                Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
            )
        } catch (e: Exception) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    @Composable
    private fun Settings(prefs: Prefs) {
        val scope = rememberCoroutineScope()
        var url by remember { mutableStateOf(prefs.civUrl) }
        var model by remember { mutableStateOf(prefs.civModel) }
        var labels by remember { mutableStateOf(prefs.showLabels) }
        var tilt by remember { mutableStateOf(prefs.tiltRotate) }
        var catchUp by remember { mutableStateOf(prefs.catchUp) }
        var speed by remember { mutableStateOf(prefs.speed) }
        var fps by remember { mutableStateOf(prefs.fps) }
        var testResult by remember { mutableStateOf("") }
        var confirmReset by remember { mutableStateOf(false) }
        var resetDone by remember { mutableStateOf(false) }
        var update by remember { mutableStateOf<UpdateInfo?>(null) }
        var updateStatus by remember { mutableStateOf("") }
        var progress by remember { mutableStateOf(-1f) }

        LaunchedEffect(Unit) {
            try {
                val info = Updater.fetchLatest()
                prefs.lastUpdateCheck = System.currentTimeMillis()
                val have = packageManager.getPackageInfo(packageName, 0).let {
                    @Suppress("DEPRECATION") it.versionCode
                }
                if (info != null && info.versionCode > have) {
                    update = info
                    updateStatus = "Update available — v${info.versionName}"
                }
            } catch (_: Exception) {
            }
        }

        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("GlassWall", style = MaterialTheme.typography.headlineMedium)
            Text(
                "A tiny village that lives on your screen. Villagers hunt, gather and build, and the settlement " +
                    "grows from a camp through huts, a village and a town into a city.",
                style = MaterialTheme.typography.bodyMedium,
            )

            update?.let { info ->
                Text(updateStatus, color = MaterialTheme.colorScheme.primary)
                if (progress >= 0f) LinearProgressIndicator(progress = { progress }, Modifier.fillMaxWidth())
                Button(onClick = {
                    if (!Updater.canInstall(this@SettingsActivity)) {
                        updateStatus = "Allow installs from GlassWall on the next screen, then tap Update again."
                        Updater.openInstallPermission(this@SettingsActivity)
                    } else scope.launch {
                        try {
                            progress = 0f
                            val apk = Updater.download(this@SettingsActivity, info) { progress = it }
                            Updater.install(this@SettingsActivity, apk)
                        } catch (e: Exception) {
                            updateStatus = "Update failed (${e.message})."
                        }
                        progress = -1f
                    }
                }) { Text("Update to v${info.versionName}") }
            }

            Button(onClick = { openPicker() }, Modifier.fillMaxWidth()) { Text("Set as wallpaper") }
            HorizontalDivider()

            Text("Villager brain (optional)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Point this at your local model server, such as Ollama over Tailscale, and the villagers decide " +
                    "what to do with it. Leave it blank and they use their built-in rules.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedTextField(
                url, { url = it; prefs.civUrl = it }, Modifier.fillMaxWidth(),
                label = { Text("Server address") }, placeholder = { Text("http://100.x.y.z:11434") }, singleLine = true,
            )
            OutlinedTextField(
                model, { model = it; prefs.civModel = it }, Modifier.fillMaxWidth(),
                label = { Text("Model name") }, singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    testResult = "Testing…"
                    scope.launch {
                        testResult = withContext(Dispatchers.IO) {
                            try {
                                val a = Llm.chat(url, model, "Reply with the single word: ready", "Are you there?", maxTokens = 300)
                                if (a.isBlank()) "Connected, but the model sent an empty answer." else "Connected. The model says: ${a.take(60)}"
                            } catch (e: Exception) {
                                "Couldn't connect: ${e.message ?: e.javaClass.simpleName}"
                            }
                        }
                    }
                }) { Text("Test connection") }
                Text(testResult, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            }
            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Names and speech bubbles")
                    Text("Show who is who and what they say", style = MaterialTheme.typography.bodySmall)
                }
                Switch(labels, { labels = it; prefs.showLabels = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Rotate with tilt")
                    Text("Portrait or landscape follows how you hold the tablet, even with auto-rotate off", style = MaterialTheme.typography.bodySmall)
                }
                Switch(tilt, { tilt = it; prefs.tiltRotate = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Keep living while hidden")
                    Text("When you come back, the village has carried on (up to 15 minutes)", style = MaterialTheme.typography.bodySmall)
                }
                Switch(catchUp, { catchUp = it; prefs.catchUp = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Village speed")
                    Text("Or double-tap the wallpaper to cycle", style = MaterialTheme.typography.bodySmall)
                }
                for (f in listOf(1, 2, 5)) {
                    if (f == speed) Button(onClick = {}) { Text("${f}x") }
                    else OutlinedButton(onClick = { speed = f; prefs.speed = f }) { Text("${f}x") }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Frame rate", Modifier.weight(1f))
                for (f in listOf(20, 30, 45, 60)) {
                    if (f == fps) Button(onClick = {}) { Text("$f") }
                    else OutlinedButton(onClick = { fps = f; prefs.fps = f }) { Text("$f") }
                }
            }
            HorizontalDivider()

            OutlinedButton(onClick = {
                if (confirmReset) {
                    Village.restart(this@SettingsActivity)
                    confirmReset = false
                    resetDone = true
                } else {
                    confirmReset = true
                    resetDone = false
                }
            }) { Text(if (confirmReset) "Tap again to start over" else "Start a new village") }
            if (resetDone) Text("A new band of villagers has settled by a fire.", style = MaterialTheme.typography.bodySmall)
            Text("v${packageManager.getPackageInfo(packageName, 0).versionName}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
