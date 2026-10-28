package com.jondude1.glasswall.data

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("glasswall", Context.MODE_PRIVATE)

    /** Address of the local model server (Ollama over Tailscale, for example). Blank means the rule-based villagers. */
    var civUrl: String
        get() = sp.getString("civUrl", "") ?: ""
        set(v) = sp.edit().putString("civUrl", v.trim()).apply()

    var civModel: String
        get() = sp.getString("civModel", "") ?: ""
        set(v) = sp.edit().putString("civModel", v.trim()).apply()

    /** Names and speech bubbles over the villagers. */
    var showLabels: Boolean
        get() = sp.getBoolean("showLabels", false)
        set(v) = sp.edit().putBoolean("showLabels", v).apply()

    /** Turn the picture with the tablet's tilt sensor, even when the screen itself is rotation-locked. */
    var tiltRotate: Boolean
        get() = sp.getBoolean("tiltRotate", true)
        set(v) = sp.edit().putBoolean("tiltRotate", v).apply()

    var fps: Int
        get() = sp.getInt("fps", 30).coerceIn(10, 60)
        set(v) = sp.edit().putInt("fps", v).apply()

    /** Whether the village keeps living (fast-forwards) while the wallpaper is hidden. */
    var catchUp: Boolean
        get() = sp.getBoolean("catchUp", true)
        set(v) = sp.edit().putBoolean("catchUp", v).apply()

    var lastUpdateCheck: Long
        get() = sp.getLong("lastUpdateCheck", 0L)
        set(v) = sp.edit().putLong("lastUpdateCheck", v).apply()
}
