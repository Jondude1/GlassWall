package com.jondude1.glasswall

import android.content.Context
import android.graphics.Paint
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import android.graphics.Typeface
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.jondude1.glasswall.ui.drawWorld

/** The village, drawn straight onto the home and lock screen. */
class VillageWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = VillageEngine()

    private inner class VillageEngine : Engine() {
        private val thread = HandlerThread("glasswall-draw").also { it.start() }
        private val handler = Handler(thread.looper)
        private val drawScope = CanvasDrawScope()
        private var visible = false
        private var width = 0
        private var height = 0
        private var hasSurface = false

        // ---- tilt: which way up the tablet is really being held (0, 1, 2, 3 quarter turns) ----
        private var gx = 0f
        private var gy = 9.8f
        private var gz = 0f
        private var physical = -1
        private var candidate = -1
        private var candidateSince = 0L
        private var fadeFrom = 0L
        private val sensors by lazy { getSystemService(Context.SENSOR_SERVICE) as SensorManager }
        private val tilt = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                gx += (e.values[0] - gx) * 0.15f
                gy += (e.values[1] - gy) * 0.15f
                gz += (e.values[2] - gz) * 0.15f
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }

        @Suppress("DEPRECATION")
        private fun displayRotation(): Int =
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation

        /** Quarter turns the picture must be rotated so it stays upright for how the tablet is held. */
        private fun turns(nowNs: Long): Int {
            val r = displayRotation()
            if (!Village.prefs(applicationContext).tiltRotate) { physical = -1; return 0 }
            val flat = kotlin.math.abs(gz) > 8.0f
            val seen = when {
                flat -> physical
                kotlin.math.abs(gy) >= kotlin.math.abs(gx) -> if (gy >= 0) 0 else 2
                else -> if (gx > 0) 1 else 3
            }
            if (physical == -1) physical = if (seen == -1) r else seen
            if (seen != -1 && seen != physical) {
                // hold steady for a moment before turning, so a wobble doesn't flip the view
                if (seen != candidate) { candidate = seen; candidateSince = nowNs }
                else if (nowNs - candidateSince > 500_000_000L) {
                    physical = seen
                    fadeFrom = nowNs
                }
            } else candidate = -1
            return ((physical - r) % 4 + 4) % 4
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(30, 30, 40)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(225, 255, 255, 255)
        }
        private val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(230, 255, 255, 255)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
        }

        private val frame = object : Runnable {
            override fun run() {
                if (!visible || !hasSurface) return
                val started = System.nanoTime()
                try {
                    drawFrame(started)
                } catch (_: Exception) {
                }
                val gapMs = 1000L / Village.prefs(applicationContext).fps
                val spent = (System.nanoTime() - started) / 1_000_000L
                handler.postDelayed(this, (gapMs - spent).coerceAtLeast(1L))
            }
        }

        private fun drawFrame(nowNs: Long) {
            val holder = surfaceHolder
            val prefs = Village.prefs(applicationContext)
            val sim = Village.sim(applicationContext)
            Village.advance(applicationContext, nowNs)
            Village.ensureGround(applicationContext, nowNs)
            val canvas = (if (android.os.Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas())
                ?: return
            try {
                val quarter = turns(nowNs)
                val sw = width.toFloat()
                val sh = height.toFloat()
                val w = if (quarter % 2 == 1) sh else sw
                val h = if (quarter % 2 == 1) sw else sh
                // on a tall screen only part of the village fits, so slowly drift across it
                val pan = if (h > w) (0.5f + 0.5f * kotlin.math.sin(sim.time.toFloat() / 38f)) else 0.5f
                canvas.save()
                canvas.translate(sw / 2f, sh / 2f)
                canvas.rotate(90f * quarter)
                canvas.translate(-w / 2f, -h / 2f)
                drawScope.draw(Density(1f), LayoutDirection.Ltr, Canvas(canvas), Size(w, h)) {
                    synchronized(Village.lock) {
                        drawWorld(sim, Village.art, true, prefs.showLabels, textPaint, bubblePaint, namePaint, pan)
                    }
                }
                canvas.restore()
                val since = (nowNs - fadeFrom) / 1_000_000L
                if (fadeFrom != 0L && since < 350L) {
                    canvas.drawColor(android.graphics.Color.argb((255 * (1f - since / 350f)).toInt(), 7, 10, 20))
                }
            } finally {
                try {
                    holder.unlockCanvasAndPost(canvas)
                } catch (_: Exception) {
                }
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            hasSurface = true
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            width = w
            height = h
            hasSurface = true
            if (visible) {
                handler.removeCallbacks(frame)
                handler.post(frame)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            hasSurface = false
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            this.visible = visible
            handler.removeCallbacks(frame)
            sensors.unregisterListener(tilt)
            if (visible) {
                sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
                    sensors.registerListener(tilt, it, SensorManager.SENSOR_DELAY_NORMAL)
                }
                physical = -1
                handler.post {
                    Village.onShown(applicationContext)
                    frame.run()
                }
            } else {
                handler.post { Village.onHidden() }
            }
        }

        override fun onDestroy() {
            visible = false
            sensors.unregisterListener(tilt)
            handler.removeCallbacksAndMessages(null)
            handler.post { Village.onHidden() }
            thread.quitSafely()
            super.onDestroy()
        }
    }
}
