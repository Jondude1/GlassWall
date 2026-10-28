package com.jondude1.glasswall

import android.graphics.Paint
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
                val w = width.toFloat()
                val h = height.toFloat()
                // on a tall screen only part of the village fits, so slowly drift across it
                val pan = if (h > w) (0.5f + 0.5f * kotlin.math.sin(sim.time.toFloat() / 38f)) else 0.5f
                drawScope.draw(Density(1f), LayoutDirection.Ltr, Canvas(canvas), Size(w, h)) {
                    synchronized(Village.lock) {
                        drawWorld(sim, Village.art, true, prefs.showLabels, textPaint, bubblePaint, namePaint, pan)
                    }
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
            if (visible) {
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
            handler.removeCallbacksAndMessages(null)
            handler.post { Village.onHidden() }
            thread.quitSafely()
            super.onDestroy()
        }
    }
}
