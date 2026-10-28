package com.jondude1.glasswall

import android.content.Context
import com.jondude1.glasswall.data.Prefs
import com.jondude1.glasswall.sim.Brain
import com.jondude1.glasswall.sim.CivSim
import com.jondude1.glasswall.sim.GH
import com.jondude1.glasswall.sim.GW
import com.jondude1.glasswall.sim.Llm
import com.jondude1.glasswall.sim.T
import com.jondude1.glasswall.ui.CivArt
import com.jondude1.glasswall.ui.GroundGen
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The one village behind every wallpaper surface (home screen, lock screen, preview).
 * Everything that touches the sim goes through the lock, so two surfaces can't tick it at once.
 */
object Village {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var simRef: CivSim? = null
    private var prefsRef: Prefs? = null
    val art = CivArt()

    private var lastTickNs = 0L
    private var lastSaveNs = 0L
    private var lastGroundCheckNs = 0L
    private var hiddenAtMs = 0L

    val lock = Any()

    fun prefs(context: Context): Prefs =
        prefsRef ?: Prefs(context).also { prefsRef = it }

    fun sim(context: Context): CivSim = synchronized(lock) {
        simRef ?: run {
            val p = prefs(context)
            val s = CivSim(File(context.applicationContext.filesDir, "village.json"), scope) {
                val url = p.civUrl
                val model = p.civModel
                if (url.isBlank() || model.isBlank()) null
                else Brain("local model", 1.0) { sys, user -> Llm.chat(url, model, sys, user) }
            }
            simRef = s
            s
        }
    }

    /** Advance the village by the real time since the last call. Call from the drawing thread. */
    fun advance(context: Context, nowNs: Long) {
        synchronized(lock) {
            val s = sim(context)
            if (lastTickNs != 0L) {
                var dt = (nowNs - lastTickNs) / 1_000_000_000.0 * prefs(context).speed
                // tick() itself clamps one step to 0.1 s, so feed it small steps
                while (dt > 0.0) {
                    val step = minOf(dt, 0.05)
                    try {
                        s.tick(step.toFloat())
                    } catch (e: Exception) {
                        s.guard(e)
                    }
                    dt -= step
                }
            }
            lastTickNs = nowNs
            if (nowNs - lastSaveNs > 30_000_000_000L) {
                lastSaveNs = nowNs
                s.save()
            }
        }
    }

    /** Called when a surface becomes visible: let the village catch up on time spent hidden. */
    fun onShown(context: Context) {
        synchronized(lock) {
            val s = sim(context)
            val gapMs = if (hiddenAtMs == 0L) 0L else System.currentTimeMillis() - hiddenAtMs
            hiddenAtMs = 0L
            lastTickNs = 0L
            if (prefs(context).catchUp && gapMs > 2_000L) {
                var ticks = (minOf(gapMs, 15 * 60_000L) / 100L).toInt()
                while (ticks-- > 0) {
                    try {
                        s.tick(0.1f)
                    } catch (e: Exception) {
                        s.guard(e)
                        break
                    }
                }
            }
        }
    }

    /** Called when no surface is visible any more. */
    fun onHidden() {
        synchronized(lock) {
            if (hiddenAtMs == 0L) hiddenAtMs = System.currentTimeMillis()
            lastTickNs = 0L
            try {
                simRef?.save()
            } catch (_: Exception) {
            }
        }
    }

    /** 1x -> 2x -> 5x -> 1x. Returns the new speed. */
    fun cycleSpeed(context: Context): Int {
        val p = prefs(context)
        p.speed = when (p.speed) { 1 -> 2; 2 -> 5; else -> 1 }
        return p.speed
    }

    fun restart(context: Context) {
        synchronized(lock) { sim(context).restart() }
    }

    /** Paints the ground image in the background whenever the shape of the land changes. */
    fun ensureGround(context: Context, nowNs: Long) {
        if (nowNs - lastGroundCheckNs < 1_500_000_000L && art.ground != null) return
        lastGroundCheckNs = nowNs
        if (art.groundBusy) return
        val s = sim(context)
        var sig = 17
        val water = BooleanArray(GW * GH)
        val grass = BooleanArray(GW * GH)
        val bio: IntArray
        synchronized(lock) {
            for (i in 0 until GW * GH) {
                sig = sig * 31 + s.biome[i] * 2 + (if (s.tiles[i] == T.WATER) 1 else 0)
                water[i] = s.tiles[i] == T.WATER
                grass[i] = s.tiles[i] == T.GRASS && s.biome[i] == 0
            }
            bio = s.biome.copyOf()
        }
        if (sig == art.groundSig) return
        art.groundBusy = true
        scope.launch {
            try {
                val fr = GroundGen.render(GW, GH, water, bio)
                val imgs = art.groundImages(fr, GW * GroundGen.CELL, GH * GroundGen.CELL)
                art.rebuildDecor(GW, GH, water, grass)
                art.ground = imgs
                art.groundSig = sig
            } catch (e: Exception) {
                art.groundSig = sig
            } finally {
                art.groundBusy = false
            }
        }
    }
}
