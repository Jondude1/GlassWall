package com.jondude1.glasswall.ui

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Pixel-art helpers with no Android types in them, so they can be tested on a plain JVM. */
object PixelFx {
    // ---- noise ----
    fun hash(x: Int, y: Int, seed: Int): Int {
        var h = x * 374761393 + y * 668265263 + seed * 1274126177
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return h and 0x7fffffff
    }

    fun rnd(x: Int, y: Int, seed: Int): Float = (hash(x, y, seed) and 0xffff) / 65535f

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    fun smooth(t: Float): Float = t * t * (3f - 2f * t)

    fun vnoise(x: Float, y: Float, seed: Int): Float {
        val x0 = floor(x).toInt()
        val y0 = floor(y).toInt()
        val fx = smooth(x - x0)
        val fy = smooth(y - y0)
        val a = rnd(x0, y0, seed)
        val b = rnd(x0 + 1, y0, seed)
        val c = rnd(x0, y0 + 1, seed)
        val d = rnd(x0 + 1, y0 + 1, seed)
        return lerp(lerp(a, b, fx), lerp(c, d, fx), fy)
    }

    fun fbm(x: Float, y: Float, seed: Int): Float =
        vnoise(x, y, seed) * 0.56f + vnoise(x * 2.1f + 7.3f, y * 2.1f + 3.1f, seed + 1) * 0.30f +
            vnoise(x * 4.3f + 1.7f, y * 4.3f + 9.4f, seed + 2) * 0.14f

    // ---- colour ----
    fun argb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun red(c: Int): Int = (c shr 16) and 0xFF
    fun green(c: Int): Int = (c shr 8) and 0xFF
    fun blue(c: Int): Int = c and 0xFF

    fun mix(a: Int, b: Int, t: Float): Int = argb(
        (red(a) + (red(b) - red(a)) * t).toInt(),
        (green(a) + (green(b) - green(a)) * t).toInt(),
        (blue(a) + (blue(b) - blue(a)) * t).toInt(),
    )

    fun tone(c: Int, f: Float): Int = argb((red(c) * f).toInt(), (green(c) * f).toInt(), (blue(c) * f).toInt())

    private val BAYER = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    /** Ordered-dither threshold for a pixel, between 0 and 1. */
    fun bayer(x: Int, y: Int): Float = (BAYER[(y and 3) * 4 + (x and 3)] + 0.5f) / 16f

    // ---- sprites ----

    /**
     * Turns a small hand-drawn sprite (0 = see-through) into a finer one at twice the size: smoother diagonals,
     * a dark selective outline, a light rim on the lit side and a little painted shading.
     */
    fun polish(src: IntArray, w: Int, h: Int, outline: Boolean = true, shade: Boolean = true): IntArray {
        val bw = w * 2
        val bh = h * 2
        fun s(x: Int, y: Int): Int = if (x < 0 || y < 0 || x >= w || y >= h) 0 else src[y * w + x]
        val big = IntArray(bw * bh)
        for (y in 0 until h) for (x in 0 until w) {
            val p = s(x, y)
            val a = s(x, y - 1)
            val b = s(x + 1, y)
            val c = s(x - 1, y)
            val d = s(x, y + 1)
            val o1 = if (c == a && c != d && a != b) a else p
            val o2 = if (a == b && a != c && b != d) b else p
            val o3 = if (d == c && d != b && c != a) c else p
            val o4 = if (b == d && b != a && d != c) d else p
            big[(2 * y) * bw + 2 * x] = o1
            big[(2 * y) * bw + 2 * x + 1] = o2
            big[(2 * y + 1) * bw + 2 * x] = o3
            big[(2 * y + 1) * bw + 2 * x + 1] = o4
        }
        fun see(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < bw && y < bh && (big[y * bw + x] ushr 24) != 0
        val edge = BooleanArray(bw * bh)
        if (outline) {
            for (y in 0 until bh) for (x in 0 until bw) {
                if (!see(x, y)) continue
                if (!see(x - 1, y) || !see(x + 1, y) || !see(x, y - 1) || !see(x, y + 1)) edge[y * bw + x] = true
            }
        }
        fun isEdge(x: Int, y: Int): Boolean = x >= 0 && y >= 0 && x < bw && y < bh && edge[y * bw + x]
        val out = IntArray(bw * bh)
        for (y in 0 until bh) for (x in 0 until bw) {
            val c = big[y * bw + x]
            if ((c ushr 24) == 0) continue
            if (edge[y * bw + x]) {
                out[y * bw + x] = argb((red(c) * 0.46f).toInt(), (green(c) * 0.46f).toInt(), (blue(c) * 0.60f).toInt())
                continue
            }
            var f = 1f
            if (shade) {
                f = 1.07f - 0.17f * (y.toFloat() / bh) + (bayer(x, y) - 0.5f) * 0.05f
                if (outline) {
                    if (isEdge(x, y - 1) || isEdge(x - 1, y)) f += 0.13f
                    if (isEdge(x + 1, y) || isEdge(x, y + 1)) f -= 0.10f
                }
            }
            out[y * bw + x] = tone(c, f)
        }
        return out
    }

    /** A black copy of a sprite with the same see-through shape, used to cast shadows. */
    fun silhouette(src: IntArray): IntArray = IntArray(src.size) { if ((src[it] ushr 24) != 0) (0xFF shl 24) else 0 }
}

/** Paints the whole ground of the village into one image, with curved coasts and ragged biome borders. */
object GroundGen {
    const val CELL = 32

    private val WATER = intArrayOf(0xFF63BCD9.toInt(), 0xFF45A0D4.toInt(), 0xFF3480C4.toInt(), 0xFF2B68AE.toInt())
    private val GRASS = intArrayOf(0xFF4A9350.toInt(), 0xFF56A354.toInt(), 0xFF62AF5A.toInt(), 0xFF70BC63.toInt())
    private val SAND = intArrayOf(0xFFD4BE7C.toInt(), 0xFFE2CF8E.toInt(), 0xFFEEDDA2.toInt())
    private val DESERT = intArrayOf(0xFFC79C54.toInt(), 0xFFD6AC62.toInt(), 0xFFE2BE78.toInt(), 0xFFEED08E.toInt())
    private val SNOW = intArrayOf(0xFFC4D4E6.toInt(), 0xFFD8E4F0.toInt(), 0xFFEAF1F8.toInt(), 0xFFFFFFFF.toInt())
    private const val WET_SAND = 0xFFBBA566.toInt()
    private const val FOAM = 0xFFEAF7FB.toInt()
    private const val FOAM_SOFT = 0xFFA8DCEC.toInt()

    private fun clamp01(v: Float): Float = if (v < 0f) 0f else if (v > 1f) 1f else v

    private fun waterPixel(px: Int, py: Int, d: Float, frame: Int, edgeNoise: Float): Int {
        val patch = (PixelFx.vnoise(px * 0.012f, py * 0.012f, 17) - 0.5f) * 0.35f
        val depth = PixelFx.smooth(clamp01(d / 0.55f + patch))
        val level = depth * 3f + (PixelFx.bayer(px, py) - 0.5f) * 0.9f
        var c = WATER[level.toInt().coerceIn(0, 3)]
        // little light dashes that shift between the two frames
        val rr = PixelFx.rnd(px / 4, py / 3, 31)
        if (rr < 0.08f && (py % 3) == 1 && ((((rr * 1000f).toInt()) + frame) and 1) == 0) {
            c = PixelFx.mix(c, 0xFFBFE6F4.toInt(), 0.75f)
        }
        // foam along the coast
        val thr = 0.050f + (if (frame == 0) 0.018f else -0.012f) + (edgeNoise - 0.5f) * 0.05f
        if (d < thr && edgeNoise > 0.22f) {
            c = FOAM
        } else if (d < thr + 0.035f && edgeNoise > 0.30f) {
            c = FOAM_SOFT
        }
        return c
    }

    private fun sandPixel(px: Int, py: Int, wet: Boolean): Int {
        val r = PixelFx.rnd(px, py, 61)
        val base = if (wet) WET_SAND else SAND[1]
        return when {
            r < 0.07f -> SAND[0]
            r > 0.94f -> SAND[2]
            else -> {
                // gentle ripples in the sand
                val ripple = PixelFx.vnoise(px * 0.09f, py * 0.22f, 63)
                if (!wet && ripple > 0.74f && (py and 1) == 0) SAND[0] else base
            }
        }
    }

    private fun grassPixel(px: Int, py: Int): Int {
        val gx = px / CELL.toFloat()
        val gy = py / CELL.toFloat()
        val n = PixelFx.fbm(gx * 1.3f, gy * 1.3f, 71)
        var level = (n * 4.2f - 0.35f) + (PixelFx.bayer(px, py) - 0.5f) * 0.85f
        // darker clumps
        val clump = PixelFx.vnoise(gx * 2.6f + 5f, gy * 2.6f + 2f, 73)
        if (clump > 0.70f) level -= 0.9f
        var c = GRASS[level.toInt().coerceIn(0, 3)]
        // blades and tiny stones
        val r = PixelFx.rnd(px / 2, py / 2, 75)
        if (r < 0.030f) {
            val sub = PixelFx.rnd(px, py, 76)
            if (sub < 0.5f) c = PixelFx.tone(GRASS[0], 0.82f)
            else if ((py and 1) == 0) c = GRASS[3]
        } else if (r > 0.994f) {
            c = 0xFF9AA3A6.toInt()
        }
        return c
    }

    private fun desertPixel(px: Int, py: Int): Int {
        val gx = px / CELL.toFloat()
        val gy = py / CELL.toFloat()
        val ridge = PixelFx.vnoise(gx * 0.45f + gy * 0.25f, gy * 0.8f - gx * 0.2f, 81) * 3.2f +
            PixelFx.vnoise(gx * 1.4f, gy * 1.4f, 82) * 0.9f
        var level = (ridge - floor(ridge)) * 2.6f + 0.2f + (PixelFx.bayer(px, py) - 0.5f) * 0.7f
        level = max(0f, min(3f, level))
        var c = DESERT[level.toInt().coerceIn(0, 3)]
        val r = PixelFx.rnd(px, py, 83)
        if (r < 0.03f) c = DESERT[0] else if (r > 0.975f) c = DESERT[3]
        return c
    }

    private fun snowPixel(px: Int, py: Int): Int {
        val gx = px / CELL.toFloat()
        val gy = py / CELL.toFloat()
        val n = PixelFx.fbm(gx * 1.1f, gy * 1.1f, 91)
        val level = n * 4.4f - 0.5f + (PixelFx.bayer(px, py) - 0.5f) * 0.8f
        var c = SNOW[level.toInt().coerceIn(0, 3)]
        val r = PixelFx.rnd(px, py, 93)
        if (r > 0.992f) c = 0xFFFFFFFF.toInt() else if (r < 0.02f) c = SNOW[0]
        return c
    }

    private fun landPixel(biome: Int, px: Int, py: Int): Int = when (biome) {
        1 -> desertPixel(px, py)
        2 -> snowPixel(px, py)
        else -> grassPixel(px, py)
    }

    /** Two frames of ground (they differ only in the water). water and biome have one entry per cell. */
    fun render(cw: Int, ch: Int, water: BooleanArray, biome: IntArray): Array<IntArray> {
        val width = cw * CELL
        val height = ch * CELL
        val f0 = IntArray(width * height)
        val f1 = IntArray(width * height)
        fun waterAt(cx: Int, cy: Int): Float {
            val x = cx.coerceIn(0, cw - 1)
            val y = cy.coerceIn(0, ch - 1)
            return if (water[y * cw + x]) 1f else 0f
        }
        fun biomeAt(cx: Int, cy: Int): Int {
            val x = cx.coerceIn(0, cw - 1)
            val y = cy.coerceIn(0, ch - 1)
            return biome[y * cw + x]
        }
        val weights = FloatArray(3)
        for (py in 0 until height) {
            for (px in 0 until width) {
                val gx = (px + 0.5f) / CELL
                val gy = (py + 0.5f) / CELL
                val wx = gx + (PixelFx.vnoise(gx * 1.7f, gy * 1.7f, 11) * 0.7f + PixelFx.vnoise(gx * 3.9f, gy * 3.9f, 12) * 0.3f - 0.5f) * 0.62f
                val wy = gy + (PixelFx.vnoise(gx * 1.7f + 31f, gy * 1.7f + 17f, 13) * 0.7f + PixelFx.vnoise(gx * 3.9f + 5f, gy * 3.9f + 2f, 14) * 0.3f - 0.5f) * 0.62f
                val sx = wx - 0.5f
                val sy = wy - 0.5f
                val ix = floor(sx).toInt()
                val iy = floor(sy).toInt()
                val fx = PixelFx.smooth(sx - ix)
                val fy = PixelFx.smooth(sy - iy)
                val m = PixelFx.lerp(
                    PixelFx.lerp(waterAt(ix, iy), waterAt(ix + 1, iy), fx),
                    PixelFx.lerp(waterAt(ix, iy + 1), waterAt(ix + 1, iy + 1), fx),
                    fy,
                )
                val d = m - 0.5f
                val i = py * width + px
                if (d > 0f) {
                    val en = PixelFx.rnd(px / 2, py / 2, 21)
                    f0[i] = waterPixel(px, py, d, 0, en)
                    f1[i] = waterPixel(px, py, d, 1, en)
                    continue
                }
                val dd = -d
                val nz = PixelFx.vnoise(px * 0.11f, py * 0.11f, 41) - 0.5f
                val c: Int
                if (dd < 0.045f + nz * 0.04f) {
                    c = sandPixel(px, py, true)
                } else if (dd < 0.19f + nz * 0.06f) {
                    c = sandPixel(px, py, false)
                } else {
                    // which biome is here: a ragged blend of the four nearest cells
                    val b00 = biomeAt(ix, iy)
                    val b10 = biomeAt(ix + 1, iy)
                    val b01 = biomeAt(ix, iy + 1)
                    val b11 = biomeAt(ix + 1, iy + 1)
                    var bio = b00
                    if (b10 != b00 || b01 != b00 || b11 != b00) {
                        weights[0] = 0f; weights[1] = 0f; weights[2] = 0f
                        weights[b00.coerceIn(0, 2)] += (1f - fx) * (1f - fy)
                        weights[b10.coerceIn(0, 2)] += fx * (1f - fy)
                        weights[b01.coerceIn(0, 2)] += (1f - fx) * fy
                        weights[b11.coerceIn(0, 2)] += fx * fy
                        var best = -1f
                        for (k in 0 until 3) {
                            val sc = weights[k] + (PixelFx.rnd(px / 2, py / 2, 45 + k) - 0.5f) * 0.34f
                            if (sc > best) { best = sc; bio = k }
                        }
                    }
                    val land = landPixel(bio, px, py)
                    c = if (dd < 0.30f + nz * 0.06f) {
                        // dithered edge where the beach turns into land
                        val t = (dd - (0.19f + nz * 0.06f)) / 0.11f
                        if (PixelFx.bayer(px, py) < t) land else sandPixel(px, py, false)
                    } else land
                }
                f0[i] = c
                f1[i] = c
            }
        }
        return arrayOf(f0, f1)
    }

}
