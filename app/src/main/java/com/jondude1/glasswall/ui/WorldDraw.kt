package com.jondude1.glasswall.ui

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jondude1.glasswall.sim.CivSim
import com.jondude1.glasswall.sim.GH
import com.jondude1.glasswall.sim.GW
import com.jondude1.glasswall.sim.T
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** The village, drawn live. It also runs the simulation while it is on screen. */

internal fun DrawScope.img(
    image: ImageBitmap, x: Float, y: Float, w: Float, h: Float,
) {
    drawImage(
        image,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(image.width, image.height),
        dstOffset = IntOffset(x.toInt(), y.toInt()),
        dstSize = IntSize(max(1, w.toInt()), max(1, h.toInt())),
        filterQuality = FilterQuality.None,
    )
}

/** Little hats and tools drawn over each villager so every job reads at a glance. */
internal fun DrawScope.jobGear(job: String, px: Float, top: Float, u: Float, working: Boolean, t: Float) {
    fun r(c: Long, x: Float, y: Float, w: Float, h: Float) =
        drawRect(Color(c), Offset(px + x * u, top + y * u), Size(w * u, h * u))
    val swing = if (working) kotlin.math.sin(t * 8f) * 0.8f else 0f
    when (job) {
        "Woodcutter" -> {
            r(0xFFC0392B, 1.5f, -0.2f, 5f, 1.2f) // red cap
            r(0xFF6B4A2B, 7.6f, 4f + swing, 0.9f, 5f) // axe handle
            r(0xFFC9CED6, 6.6f, 4f + swing, 2f, 1.5f) // axe head
        }
        "Miner" -> {
            r(0xFFF2C230, 1.2f, -0.8f, 5.6f, 1.8f) // helmet
            r(0xFFFFF6B0, 3.4f, -0.4f, 1.2f, 0.8f) // lamp
            r(0xFF6B4A2B, 7.6f, 4.5f - swing, 0.9f, 5f) // pick handle
            r(0xFF9AA0AA, 6.4f, 4.2f - swing, 3f, 0.9f)
        }
        "Forager" -> {
            r(0xFF4E9E4A, 1.8f, -0.2f, 4.4f, 1.1f) // leafy band
            r(0xFFA67E4B, 6.2f, 8f, 3f, 2.4f) // basket
            r(0xFFE0405A, 6.8f, 7.6f, 0.9f, 0.9f)
            r(0xFFE0405A, 8f, 7.8f, 0.9f, 0.9f)
        }
        "Farmer" -> {
            r(0xFFE8C547, -0.8f, 0.6f, 9.6f, 0.9f) // straw brim
            r(0xFFD9AE2F, 1.8f, -1f, 4.4f, 1.8f) // crown
            r(0xFF6B4A2B, 7.8f, 3f + swing * 0.5f, 0.8f, 7f) // hoe
            r(0xFF9AA0AA, 7f, 9.6f + swing * 0.5f, 2.4f, 0.8f)
        }
        "Builder" -> {
            r(0xFFEF7B1E, 1.2f, -0.8f, 5.6f, 1.8f) // hard hat
            r(0xFFEF7B1E, 0.6f, 0.6f, 6.8f, 0.6f)
            r(0xFF6B4A2B, 7.6f, 5f + swing, 0.9f, 3.5f) // hammer
            r(0xFF6E6E78, 6.8f, 4.4f + swing, 2.5f, 1.2f)
        }
        "Scout" -> {
            r(0xFF2F7F52, 1.2f, -0.6f, 5.6f, 1.6f) // green cap
            r(0xFFE0405A, 6f, -2f, 0.9f, 2.4f) // feather
            r(0xFF8A5A2B, -0.3f, 5f, 1f, 6f) // walking stick
        }
        "Storyteller" -> {
            r(0xFF6D3AA8, 1.6f, -0.6f, 4.8f, 1.4f)
            r(0xFF8E4FD9, 2.6f, -2f, 2.8f, 1.6f)
            r(0xFF8E4FD9, 3.4f, -3.2f, 1.2f, 1.4f)
            r(0xFFFFE066, 3.7f, -2.8f, 0.6f, 0.6f) // star
        }
        "Hunter" -> {
            r(0xFF5A6B2E, 1.4f, -0.4f, 5.2f, 1.3f) // hood
            r(0xFF8A5A2B, 7.6f, 3f, 0.8f, 7f) // bow
            r(0xFFEFEFEF, 8.4f, 3.2f, 0.3f, 6.6f) // string
            r(0xFF8A5A2B, 6.6f, 3f, 1.4f, 0.8f)
            r(0xFF8A5A2B, 6.6f, 9.4f, 1.4f, 0.8f)
        }
        "Bronzesmith", "Blacksmith" -> {
            r(if (job == "Blacksmith") 0xFF2E2E34 else 0xFF8B5A2B, 1.4f, -0.4f, 5.2f, 1.3f) // cap
            r(0xFF3B2A1A, 2f, 6f, 4f, 4.2f) // apron
            r(0xFF6B4A2B, 7.6f, 5f + swing, 0.9f, 3.5f) // hammer
            r(if (job == "Blacksmith") 0xFF8A8A94 else 0xFFCD7F32, 6.8f, 4.4f + swing, 2.5f, 1.4f)
        }
    }
}

private fun cellHash(x: Int, y: Int): Int = ((x * 73856093) xor (y * 19349663)) and 0x7fffffff

private fun DrawScope.shadow(cx: Float, cy: Float, w: Float, h: Float) {
    drawOval(Color.Black.copy(alpha = 0.22f), topLeft = Offset(cx - w / 2f, cy - h / 2f), size = Size(w, h))
}

private fun DrawScope.banner(x: Float, baseY: Float, h: Float, c: Color, ps: Float, t: Float, seed: Float) {
    drawRect(Color(0xFF4A3220), Offset(x, baseY - h), Size(ps * 0.9f, h))
    val parts = 4
    val cw = ps * 1.3f
    for (i in 0 until parts) {
        val off = kotlin.math.sin(t * 4f + seed + i * 0.9f) * ps * 0.5f
        val fx0 = x + ps * 0.9f + i * cw
        drawRect(c, Offset(fx0, baseY - h + off), Size(cw + 0.5f, ps * 3.2f))
        drawRect(Color.Black.copy(alpha = 0.18f), Offset(fx0, baseY - h + off + ps * 2.2f), Size(cw + 0.5f, ps))
    }
}

internal fun DrawScope.drawWorld(
    sim: CivSim, art: CivArt, cover: Boolean, labels: Boolean,
    textPaint: Paint, bubblePaint: Paint, namePaint: Paint,
    pan: Float = 0.5f,
) {
    val ts = if (cover) max(size.width / GW, size.height / GH) else min(size.width / GW, size.height / GH)
    val shk = sim.shake()
    val ox = (size.width - GW * ts) * pan + kotlin.math.sin(sim.time * 70.0).toFloat() * ts * 0.18f * shk
    val oy = (size.height - GH * ts) / 2f + kotlin.math.cos(sim.time * 63.0).toFloat() * ts * 0.18f * shk
    val ps = ts / 16f
    val t = sim.time.toFloat()
    val water = ((sim.time * 1.5).toInt()) and 1
    val fireFrame = ((sim.time * 4).toInt()) and 1
    val dark = sim.darkness()
    val geo = Geo(ox, oy, ts, ps, t, dark)
    val sun = sunFor(sim, dark)

    drawRect(Color(0xFF0B1220))

    fun isWater(x: Int, y: Int): Boolean =
        x in 0 until GW && y in 0 until GH && sim.tiles[y * GW + x] == T.WATER

    // ground: one painted image with curved coasts, beaches and ragged biome borders
    val gnd = art.ground
    if (gnd != null) img(gnd[water], ox, oy, GW * ts, GH * ts)
    // twinkles on the water and flowers in the grass
    for (y in 0 until GH) for (x in 0 until GW) {
        val i = y * GW + x
        val h = cellHash(x, y)
        if (sim.tiles[i] == T.WATER && h % 5 == 0) {
            val tw = kotlin.math.sin(t * 2.4f + (h % 13)).coerceIn(0f, 1f)
            if (tw > 0.05f) {
                val sx = ox + x * ts + ((h shr 3) % 12 + 2) * ps
                val sy = oy + y * ts + ((h shr 7) % 12 + 2) * ps
                drawRect(Color.White.copy(alpha = 0.85f * tw), Offset(sx, sy), Size(ps * 1.5f, ps * 1.5f))
                drawRect(Color.White.copy(alpha = 0.4f * tw), Offset(sx - ps, sy + ps * 0.4f), Size(ps * 3.5f, ps * 0.7f))
            }
        }
        if (sim.tiles[i] == T.GRASS && sim.biome[i] == 0 && h % 9 == 0) {
            val fc = when ((h / 9) % 3) {
                0 -> Color(0xFFFFFFFF)
                1 -> Color(0xFFFFE066)
                else -> Color(0xFFFF8FB8)
            }
            val fx = ox + x * ts + ((h shr 4) % 11 + 2) * ps
            val fy = oy + y * ts + ((h shr 8) % 10 + 3) * ps
            val nod = kotlin.math.sin(t * 1.6f + (h % 9)) * ps * 0.35f
            drawRect(Color(0xFF3F8744), Offset(fx + ps * 0.4f, fy + ps), Size(ps * 0.7f, ps * 1.6f))
            drawRect(fc, Offset(fx + nod, fy), Size(ps * 1.6f, ps * 1.6f))
        }
    }

    // kingdom territories: tinted ground and glowing borders
    val pulse = 0.65f + 0.2f * kotlin.math.sin(t * 2f)
    fun other(nx: Int, ny: Int, o: Int) = nx in 0 until GW && ny in 0 until GH && sim.owner[ny * GW + nx] != o
    for (y in 0 until GH) for (x in 0 until GW) {
        val o = sim.owner[y * GW + x]
        if (o < 0) continue
        val kg = sim.kingdoms.getOrNull(o) ?: continue
        val kc = Color(kg.color)
        drawRect(kc.copy(alpha = 0.13f), Offset(ox + x * ts, oy + y * ts), Size(ts + 1f, ts + 1f))
        val bw = ps * 1.4f
        val edge = kc.copy(alpha = pulse)
        if (other(x - 1, y, o)) drawRect(edge, Offset(ox + x * ts, oy + y * ts), Size(bw, ts))
        if (other(x + 1, y, o)) drawRect(edge, Offset(ox + (x + 1) * ts - bw, oy + y * ts), Size(bw, ts))
        if (other(x, y - 1, o)) drawRect(edge, Offset(ox + x * ts, oy + y * ts), Size(ts, bw))
        if (other(x, y + 1, o)) drawRect(edge, Offset(ox + x * ts, oy + (y + 1) * ts - bw), Size(ts, bw))
    }

    lifeUnderlay(sim, art, geo)

    // ships sail along open water
    var shipN = 0
    for (y in 1 until GH - 1) {
        var x = 0
        while (x < GW) {
            if (isWater(x, y)) {
                var e = x
                while (e < GW && isWater(e, y)) e++
                if (e - x >= 5 && shipN < 3) {
                    val lo = x + 1.5f
                    val hi = e - 1.5f
                    val pos = lo + (hi - lo) * (0.5f + 0.5f * kotlin.math.sin(t * 0.12f + y))
                    val sp = art.ship
                    val sw = sp.lw * ps
                    val sh = sp.lh * ps
                    val bob = kotlin.math.sin(t * 2f + y) * ps * 0.7f
                    val cx = ox + pos * ts
                    val cy = oy + (y + 0.7f) * ts + bob
                    drawOval(Color.Black.copy(alpha = 0.18f), Offset(cx - sw * 0.45f, cy - ps), Size(sw * 0.9f, ps * 2.4f))
                    img(sp, cx - sw / 2f, cy - sh, sw, sh)
                    shipN++
                }
                x = e
            } else {
                x++
            }
        }
    }

    val wellHut = sim.buildings.firstOrNull { it.type == "hut" && it.era >= 1 }
    // things standing on the ground, row by row so lower ones overlap higher ones
    for (y in 0 until GH) {
        for (x in 0 until GW) {
            val i = y * GW + x
            val h = cellHash(x, y)
            var sp: ImageBitmap? = null
            var shadowW = 0f
            var swaying = false
            when (sim.tiles[i]) {
                T.FOREST -> {
                    if (sim.amt[i] <= 2) {
                        sp = art.stump
                    } else {
                        sp = if (h % 3 == 0 || sim.biome[i] == 2) art.pine else art.oak
                        shadowW = 10f
                        swaying = true
                    }
                }
                T.ROCK -> { sp = art.rock; shadowW = 11f }
                T.ORE -> { sp = art.oreRock; shadowW = 11f }
                T.BUSH -> { sp = if (sim.amt[i] > 0) art.bush else art.bushEmpty; shadowW = 9f }
                T.GRASS -> if (sim.biome[i] == 1 && h % 11 == 0 && sim.owner[i] < 0) { sp = art.cactus; shadowW = 6f }
            }
            if (sp != null) {
                val w = sp.lw * ps
                val hh = sp.lh * ps
                val cx = ox + (x + 0.5f) * ts
                val baseY = oy + (y + 0.95f) * ts
                if (shadowW > 0f) {
                    shadow(cx, baseY, shadowW * ps, 3.5f * ps)
                    castShadow(art, sp, cx, baseY, w, hh, sun)
                }
                val sway = if (swaying) kotlin.math.sin(t * 1.3f + (h % 7)) * ps * 0.6f else 0f
                img(sp, cx - w / 2f + sway, baseY - hh, w, hh)
            }
        }
        if (wellHut != null && wellHut.y == y) {
            val wsp = art.well
            val ww = wsp.lw * ps
            val wh = wsp.lh * ps
            val wcx = ox + (wellHut.x + 1.5f) * ts
            val wby = oy + (wellHut.y + 0.97f) * ts
            shadow(wcx, wby, ww * 0.9f, 3.5f * ps)
            castShadow(art, wsp, wcx, wby, ww, wh, sun)
            img(wsp, wcx - ww / 2f, wby - wh, ww, wh)
        }
        for (k in sim.kingdoms) {
            if (k.id == 0) continue
            val kc = Color(k.color)
            if (k.fallen) {
                if (k.cy == y) {
                    val rs = art.rock
                    img(rs, ox + (k.cx + 0.5f) * ts - rs.lw * ps / 2f, oy + (k.cy + 0.95f) * ts - rs.lh * ps, rs.lw * ps, rs.lh * ps)
                }
                continue
            }
            val home = art.homeFor(k.race)
            val n = sim.homeCount(k)
            for ((hi, hm) in k.homes.withIndex()) {
                if (hi >= n || hm[1] != y) continue
                val w = home.lw * ps
                val hh = home.lh * ps
                val cx = ox + (hm[0] + 0.5f) * ts
                val baseY = oy + (hm[1] + 0.97f) * ts
                shadow(cx, baseY, w * 0.9f, 3.5f * ps)
                castShadow(art, home, cx, baseY, w, hh, sun)
                img(home, cx - w / 2f, baseY - hh, w, hh)
                banner(cx + w * 0.28f, baseY - hh + ps * 1.5f, ps * 5f, kc, ps * 0.8f, t, hi.toFloat())
            }
            if (k.cy == y) {
                val kp = art.keep
                val w = kp.lw * ps
                val hh = kp.lh * ps
                val cx = ox + (k.cx + 0.5f) * ts
                val baseY = oy + (k.cy + 0.97f) * ts
                shadow(cx, baseY, w * 0.95f, 4f * ps)
                castShadow(art, kp, cx, baseY, w, hh, sun)
                img(kp, cx - w / 2f, baseY - hh, w, hh)
                banner(cx - ps * 0.4f, baseY - hh + ps * 0.5f, ps * 9f, kc, ps, t, k.id.toFloat())
                if (dark > 0.1f) {
                    val glow = Color(0xFFFFD27A).copy(alpha = min(1f, dark * 1.6f))
                    drawRect(glow, Offset(cx - w * 0.31f, baseY - hh * 0.72f), Size(ps * 1.4f, ps * 2.2f))
                    drawRect(glow, Offset(cx + w * 0.15f, baseY - hh * 0.72f), Size(ps * 1.4f, ps * 2.2f))
                }
            }
        }
        val myFire = sim.buildings.firstOrNull { it.type == "campfire" }
        if (myFire != null && myFire.y == y) {
            banner(ox + (myFire.x + 1.15f) * ts, oy + (myFire.y + 0.9f) * ts, ps * 11f, Color(sim.kingdoms[0].color), ps, t, 0f)
        }
        for (b in sim.buildings) {
            if (b.y != y || b.type != "farm") continue
            for (k in 0 until 2) {
                val sp = art.chicken[if (((t * 2f + k * 3f).toInt() and 3) == 0) 1 else 0]
                val cw = sp.lw * ps
                val chh = sp.lh * ps
                val cx = ox + (b.x + 0.5f + kotlin.math.sin(t * 0.3f + k * 2.6f) * 0.9f) * ts
                val cy = oy + (b.y + 0.9f + 0.12f * k) * ts
                shadow(cx, cy, cw * 0.9f, 1.8f * ps)
                img(sp, cx - cw / 2f, cy - chh, cw, chh)
            }
        }
        for (b in sim.buildings) {
            if (b.y != y) continue
            val sp = when (b.type) {
                "hut" -> when {
                    b.era <= 0 -> art.tent
                    b.era == 1 -> art.hut
                    b.era == 2 -> art.hutStone
                    b.era == 3 -> art.townHouse
                    else -> art.cityHouse
                }
                "hall" -> if (b.era <= 2) art.hall else art.keep
                "farm" -> art.farm
                "smelter" -> art.forge
                "tower" -> art.tower
                "palisade" -> art.palisade
                else -> art.fire[fireFrame]
            }
            val w = sp.lw * ps
            val hh = sp.lh * ps
            val cx = ox + (b.x + 0.5f) * ts
            val baseY = oy + (b.y + 0.97f) * ts
            if (b.type != "farm") {
                shadow(cx, baseY, w * 0.9f, 3.5f * ps)
                if (b.type != "campfire") castShadow(art, sp, cx, baseY, w, hh, sun)
            }
            img(sp, cx - w / 2f, baseY - hh, w, hh)
            if (b.type == "farm") crops(b, geo, cx - w / 2f, baseY - hh)
            if (b.type == "hall" && b.era >= 3) {
                banner(cx - ps * 0.4f, baseY - hh + ps * 0.5f, ps * 9f, Color(sim.kingdoms[0].color), ps, t, 7f)
            }
            if (b.type == "tower") {
                // a flag on top and a torch that glows at night
                banner(cx + ps * 0.5f, baseY - hh + ps * 0.5f, ps * 8f, Color(sim.kingdoms[0].color), ps, t, 3f)
                if (dark > 0.1f) {
                    drawCircle(
                        Color(0xFFFFB347).copy(alpha = min(0.5f, dark * 0.9f)),
                        radius = ts * (0.9f + 0.08f * kotlin.math.sin(t * 7f)),
                        center = Offset(cx, baseY - hh * 0.75f),
                    )
                    drawRect(Color(0xFFFFE070), Offset(cx - ps, baseY - hh * 0.8f), Size(ps * 2f, ps * 2.4f))
                }
            }
            // lit windows at night
            if ((b.type == "hut" || b.type == "hall") && b.era >= 1 && dark > 0.1f) {
                val glow = Color(0xFFFFD27A).copy(alpha = min(1f, dark * 1.6f))
                drawRect(glow, Offset(cx - w * 0.36f, baseY - hh * 0.5f), Size(ps * 2f, ps * 2.4f))
                drawRect(glow, Offset(cx + w * 0.18f, baseY - hh * 0.5f), Size(ps * 2f, ps * 2.4f))
            }
        }
    }

    // smoke from campfires and forges
    for (b in sim.buildings) {
        if (b.type != "campfire" && b.type != "smelter") continue
        val fx = ox + (b.x + 0.5f) * ts
        val fy = oy + (b.y + 0.4f) * ts
        for (k in 0 until 4) {
            val p = ((t * 0.45f + k * 0.25f) % 1f)
            drawCircle(
                Color(0xFFC8C8D0).copy(alpha = 0.32f * (1f - p)),
                radius = ts * (0.08f + 0.2f * p),
                center = Offset(fx + kotlin.math.sin(p * 6f + k) * ts * 0.14f, fy - p * ts * 1.8f),
            )
        }
    }

    // wildlife, then villagers
    lifeCritters(sim, art, geo)
    drawDrops(sim, geo)
    val sorted = sim.villagers.sortedBy { it.y }
    drawVillagers(sim, art, geo, sun)

    // effects (bolts, meteors, fires, clashes, arrows)
    for (f in sim.fx.toList()) {
        val age = (sim.time - f.born).toFloat()
        val p = (age / f.life).coerceIn(0f, 1f)
        val cx = ox + f.x * ts
        val cy = oy + f.y * ts
        when (f.type) {
            "bolt" -> if (age < 0.5f) {
                val a = 1f - age / 0.5f
                var px0 = cx + ts * 0.6f
                var py0 = oy - ts
                val steps = 7
                for (sg in 1..steps) {
                    val last = sg == steps
                    val nx = if (last) cx else cx + (((cellHash(sg, (f.born * 10).toInt()) % 100) / 100f) - 0.5f) * ts * 1.4f
                    val ny = py0 + (cy - (oy - ts)) / steps
                    drawLine(Color.White.copy(alpha = a), Offset(px0, py0), Offset(nx, ny), strokeWidth = ps * 2.4f)
                    drawLine(Color(0xFFFFE066).copy(alpha = a), Offset(px0, py0), Offset(nx, ny), strokeWidth = ps * 1f)
                    px0 = nx
                    py0 = ny
                }
                drawRect(Color.White.copy(alpha = 0.35f * a))
                drawCircle(Color(0xFFFFF3B0).copy(alpha = a), radius = ts * (0.4f + age), center = Offset(cx, cy))
            }
            "arrow" -> {
                val a = 1f - p
                val ax = cx - ts * 1.2f * (1f - p)
                val ay = cy - ts * 2.2f * (1f - p)
                drawLine(Color(0xFFF2E6C8).copy(alpha = a), Offset(ax, ay), Offset(ax + ts * 0.35f, ay + ts * 0.5f), strokeWidth = ps * 1.1f)
                drawCircle(Color(0xFFFFE9A0).copy(alpha = 0.6f * p), radius = ts * 0.25f * p, center = Offset(cx, cy))
            }
            "fire" -> {
                val fade = if (p > 0.7f) (1f - p) / 0.3f else 1f
                for (k in 0 until 5) {
                    val ph = ((t * 2.2f + k * 0.21f) % 1f)
                    val fxp = cx + (k - 2) * ps * 2.2f
                    val col = if (k % 2 == 0) Color(0xFFFF8A2B) else Color(0xFFFFD84A)
                    drawRect(col.copy(alpha = fade * (1f - ph)), Offset(fxp, cy - ph * ts * 0.9f), Size(ps * 2.4f, ps * (3.2f - ph * 2f)))
                }
            }
            "bless" -> {
                drawCircle(Color(0xFFFFE9A0).copy(alpha = 0.55f * (1f - p)), radius = ts * (0.5f + p * 4.5f), center = Offset(cx, cy))
                for (k in 0 until 14) {
                    val ang = k * 0.45f + p * 2f
                    val rr = ts * (0.4f + p * 3.6f) * (0.5f + (k % 3) * 0.25f)
                    drawRect(
                        Color(0xFFFFF6B0).copy(alpha = 1f - p),
                        Offset(cx + kotlin.math.cos(ang) * rr, cy + kotlin.math.sin(ang) * rr * 0.7f - p * ts * 1.4f),
                        Size(ps * 1.8f, ps * 1.8f),
                    )
                }
            }
            "spawn" -> {
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - p)), radius = ts * (0.3f + p * 1.8f), center = Offset(cx, cy))
                drawRect(
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.8f * (1f - p)), Color.Transparent), startY = cy - ts * 3f, endY = cy),
                    Offset(cx - ts * 0.3f, cy - ts * 3f),
                    Size(ts * 0.6f, ts * 3f),
                )
            }
            "quake" -> {
                for (k in 0 until 7) {
                    val ang = k * 0.9f
                    val len = ts * (1f + 2.2f * minOf(1f, p * 2f))
                    drawLine(
                        Color(0xFF2A1A10).copy(alpha = 1f - p * 0.6f),
                        Offset(cx, cy),
                        Offset(cx + kotlin.math.cos(ang) * len, cy + kotlin.math.sin(ang) * len * 0.7f),
                        strokeWidth = ps * 1.3f,
                    )
                }
                for (k in 0 until 6) {
                    val ang = k * 1.05f
                    drawCircle(
                        Color(0xFFB09870).copy(alpha = 0.35f * (1f - p)),
                        radius = ts * (0.25f + p * 0.6f),
                        center = Offset(cx + kotlin.math.cos(ang) * ts * 1.4f * p, cy + kotlin.math.sin(ang) * ts * p - p * ts * 0.6f),
                    )
                }
            }
            "meteor" -> {
                if (p < 0.45f) {
                    val q = p / 0.45f
                    val sx = cx + ts * 9f * (1f - q)
                    val sy = cy - ts * 12f * (1f - q)
                    drawLine(Color(0xFFFF8A2B).copy(alpha = 0.6f), Offset(sx + ts * 2f, sy - ts * 2.6f), Offset(sx, sy), strokeWidth = ps * 4f)
                    drawCircle(Color(0xFFFFE066), radius = ts * 0.55f, center = Offset(sx, sy))
                    drawCircle(Color(0xFFFF6A2B), radius = ts * 0.4f, center = Offset(sx, sy))
                } else {
                    val q = (p - 0.45f) / 0.55f
                    drawRect(Color.White.copy(alpha = 0.4f * (1f - q) * (1f - q)))
                    drawCircle(Color(0xFFFF8A2B).copy(alpha = 0.7f * (1f - q)), radius = ts * (0.5f + q * 3.2f), center = Offset(cx, cy))
                    drawCircle(Color(0xFFFFE066).copy(alpha = 0.8f * (1f - q)), radius = ts * (0.3f + q * 1.6f), center = Offset(cx, cy))
                    for (k in 0 until 8) {
                        val ang = k * 0.8f
                        drawRect(
                            Color(0xFFFFC94A).copy(alpha = 1f - q),
                            Offset(cx + kotlin.math.cos(ang) * ts * 3.6f * q, cy + kotlin.math.sin(ang) * ts * 3f * q - ts * q),
                            Size(ps * 2f, ps * 2f),
                        )
                    }
                }
            }
            "plague" -> {
                val fade = if (p > 0.6f) (1f - p) / 0.4f else minOf(1f, p * 4f)
                for (k in 0 until 9) {
                    val ang = k * 0.7f + t * 0.4f
                    val rr = ts * (1.2f + (k % 3) * 1.1f)
                    drawCircle(
                        Color(0xFF8EDB4A).copy(alpha = 0.22f * fade),
                        radius = ts * (0.9f + (k % 2) * 0.5f),
                        center = Offset(cx + kotlin.math.cos(ang) * rr, cy + kotlin.math.sin(ang) * rr * 0.7f - ts * 0.4f),
                    )
                }
            }
        }
    }

    // butterflies by day, small birds crossing over the village
    if (dark < 0.25f) {
        for (k in 0 until 5) {
            val bx = ox + (GW * 0.5f + kotlin.math.sin(t * 0.21f + k * 1.9f) * GW * 0.38f) * ts
            val by = oy + (GH * 0.5f + kotlin.math.cos(t * 0.17f + k * 2.7f) * GH * 0.36f) * ts
            val flap = kotlin.math.abs(kotlin.math.sin(t * 14f + k * 3f))
            val c = when (k % 3) { 0 -> Color(0xFFFFE066); 1 -> Color(0xFFFF8FB8); else -> Color(0xFFBFE3FF) }
            drawRect(c, Offset(bx - ps * 2.2f * flap - ps * 0.5f, by - ps), Size(ps * 2.2f * flap + ps * 0.5f, ps * 1.8f))
            drawRect(c, Offset(bx + ps * 0.3f, by - ps), Size(ps * 2.2f * flap + ps * 0.5f, ps * 1.8f))
            drawRect(Color(0xFF3B2A1A), Offset(bx - ps * 0.3f, by - ps * 0.8f), Size(ps * 0.8f, ps * 1.8f))
        }
        val span2 = GW * ts + 8f * ts
        for (k in 0 until 3) {
            val bx = ox - 4f * ts + ((t * ts * (0.9f + 0.3f * k) + k * span2 / 3f) % span2)
            val by = oy + (GH * (0.2f + 0.25f * k) + kotlin.math.sin(t * 0.8f + k) * 1.2f) * ts
            val wing = kotlin.math.sin(t * 9f + k * 2f) * ps * 1.4f
            val col = Color(0xFF2A2A33).copy(alpha = 0.75f)
            drawLine(col, Offset(bx - ps * 2.5f, by - wing), Offset(bx, by), strokeWidth = ps * 0.9f)
            drawLine(col, Offset(bx + ps * 2.5f, by - wing), Offset(bx, by), strokeWidth = ps * 0.9f)
            drawOval(Color.Black.copy(alpha = 0.12f), Offset(bx - ps * 2f, by + ts * 1.6f), Size(ps * 4f, ps * 1.6f))
        }
    }

    // peaceful deer graze at the edges and glow-eyed beasts show at night
    if (dark < 0.5f) {
        for (k in 0 until 3) {
            val dx = ox + (GW * (0.12f + 0.3f * k) + kotlin.math.sin(t * 0.07f + k * 2.1f) * 2.5f) * ts
            val dy = oy + (GH * (0.86f - 0.04f * k) + kotlin.math.cos(t * 0.05f + k) * 0.8f) * ts
            val sp = art.deer[if (((t * 1.2f + k).toInt() and 3) == 0) 1 else 0]
            val dw = sp.lw * ps
            val dh = sp.lh * ps
            shadow(dx, dy, dw * 0.9f, 2.2f * ps)
            img(sp, dx - dw / 2f, dy - dh, dw, dh)
        }
    }

    // predators
    for (p in sim.predators) {
        val sp = when (p.kind) { "bear" -> art.bear; "boar" -> art.boar; else -> art.wolf }
        val sc = when (p.kind) { "bear" -> 1.15f; "boar" -> 0.95f; else -> 1f }
        val w = sp.lw * ps * sc
        val h = sp.lh * ps * sc
        val feet = oy + (p.y + 0.45f) * ts
        shadow(ox + p.x * ts, feet, w * 0.9f, 2.6f * ps)
        img(sp, ox + p.x * ts - w / 2f, feet - h, w, h)
        val maxHp = com.jondude1.glasswall.sim.predatorMaxHp(p.kind)
        if (p.hp < maxHp) {
            drawRect(Color(0xAA000000), Offset(ox + p.x * ts - w / 2f, feet - h - ps * 2.5f), Size(w, ps * 1.4f))
            drawRect(Color(0xFFE0453A), Offset(ox + p.x * ts - w / 2f, feet - h - ps * 2.5f), Size(w * (p.hp / maxHp).coerceIn(0f, 1f), ps * 1.4f))
        }
    }

    // drifting cloud shadows in daylight
    val cloudAlpha = 0.10f * (1f - dark / 0.55f)
    if (cloudAlpha > 0.005f) {
        val span = GW * ts + 14f * ts
        for (k in 0 until 4) {
            val cx = ox - 7f * ts + ((t * 0.9f * ts * 0.12f + k * span / 4f) % span)
            val cy = oy + ((GH * (0.18f + 0.22f * k)) % GH) * ts
            drawOval(
                Color.Black.copy(alpha = cloudAlpha),
                topLeft = Offset(cx - ts * 5f, cy - ts * 1.6f),
                size = Size(ts * 10f, ts * 3.2f),
            )
        }
    }

    // drifting smoke from fires and forges
    for (b in sim.buildings) {
        if (b.type != "campfire" && b.type != "smelter") continue
        val bx = ox + (b.x + 0.5f) * ts
        val by = oy + (b.y + 0.2f) * ts
        for (k in 0 until 5) {
            val ph = ((t * 0.45f + k * 0.2f + b.x * 0.13f) % 1f)
            val rise = ph * ts * 2.6f
            val sway = kotlin.math.sin(t * 1.3f + k * 2f + b.y) * ts * 0.18f * ph
            val a = (1f - ph) * 0.32f
            drawCircle(Color(0xFFD8D4D0).copy(alpha = a), radius = ts * (0.10f + 0.16f * ph), center = Offset(bx + sway + ph * ts * 0.3f, by - rise))
        }
    }

    // dusk and dawn glow warm before the dark comes in
    val warmth = (1f - kotlin.math.abs(dark - 0.22f) / 0.22f).coerceIn(0f, 1f)
    if (warmth > 0f) drawRect(Color(0xFFFF8A3C).copy(alpha = 0.13f * warmth))

    // evening and night
    if (dark > 0f) {
        // the dark, with holes cut out wherever there is a fire or a window
        drawIntoCanvas { c ->
            val nc = c.nativeCanvas
            val bounds = RectF(0f, 0f, size.width, size.height)
            val layer = nc.saveLayer(bounds, null)
            art.nightPaint.color = android.graphics.Color.argb((dark * 255f * 1.15f).toInt().coerceIn(0, 255), 8, 14, 46)
            nc.drawRect(bounds, art.nightPaint)
            fun light(lx: Float, ly: Float, rad: Float, strength: Float) {
                val a1 = (strength * 255f).toInt().coerceIn(0, 255)
                art.lightPaint.shader = android.graphics.RadialGradient(
                    lx, ly, rad,
                    intArrayOf(android.graphics.Color.argb(a1, 0, 0, 0), android.graphics.Color.argb(a1 * 55 / 100, 0, 0, 0), 0),
                    floatArrayOf(0f, 0.45f, 1f),
                    android.graphics.Shader.TileMode.CLAMP,
                )
                nc.drawCircle(lx, ly, rad, art.lightPaint)
            }
            for (b in sim.buildings) {
                val bx = ox + (b.x + 0.5f) * ts
                val by = oy + (b.y + 0.5f) * ts
                val flick = 1f + 0.06f * kotlin.math.sin(t * 11f + b.x)
                when {
                    b.type == "campfire" -> light(bx, by, ts * 5.5f * flick, 0.95f)
                    b.type == "smelter" -> light(bx, by, ts * 3.4f * flick, 0.75f)
                    b.type == "tower" -> light(bx, by - ts * 0.8f, ts * 4f * flick, 0.8f)
                    b.type == "hut" && b.era >= 1 -> light(bx, by + ts * 0.1f, ts * 2.2f, 0.6f)
                    b.type == "hall" -> light(bx, by, ts * 3.4f, 0.75f)
                }
            }
            for (k in sim.kingdoms) {
                if (k.id == 0 || k.fallen) continue
                light(ox + (k.cx + 0.5f) * ts, oy + (k.cy + 0.4f) * ts, ts * 3.2f, 0.7f)
                for ((hi, hm) in k.homes.withIndex()) {
                    if (hi >= sim.homeCount(k)) break
                    light(ox + (hm[0] + 0.5f) * ts, oy + (hm[1] + 0.5f) * ts, ts * 1.8f, 0.5f)
                }
            }
            art.lightPaint.shader = null
            nc.restoreToCount(layer)
        }
        // warm glow on top of the cut-out light
        for (b in sim.buildings) {
            if (b.type != "campfire" && b.type != "smelter" && b.type != "tower") continue
            val gc = Offset(ox + (b.x + 0.5f) * ts, oy + (b.y + (if (b.type == "tower") -0.3f else 0.5f)) * ts)
            val flick = (1f + 0.06f * kotlin.math.sin(t * 11f)) * (if (b.type == "campfire") 1f else 0.5f)
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFFFB347).copy(alpha = dark * 0.55f), Color.Transparent),
                    center = gc,
                    radius = ts * 4f * flick,
                ),
                radius = ts * 4f * flick,
                center = gc,
                blendMode = BlendMode.Plus,
            )
        }
        for (b in sim.buildings) {
            if (b.type != "hut") continue
            val wx = ox + (b.x + 0.5f) * ts
            val wy = oy + (b.y + 0.62f) * ts
            drawCircle(Color(0xFFFFC857).copy(alpha = 0.22f * dark), radius = ts * 0.55f, center = Offset(wx, wy))
            drawRect(Color(0xFFFFE08A).copy(alpha = 0.9f * min(1f, dark * 1.6f)), Offset(wx - ps * 1.5f, wy - ps * 1.5f), Size(ps * 3f, ps * 3f))
        }
        for (p in sim.predators) {
            val ex = ox + p.x * ts
            val ey = oy + (p.y - 0.1f) * ts
            val blink = if (kotlin.math.sin(t * 1.7f + p.x) > 0.96f) 0f else 1f
            val ec = Color(0xFFFF3B2F).copy(alpha = 0.95f * blink)
            drawCircle(Color(0xFFFF3B2F).copy(alpha = 0.18f * blink), radius = ts * 0.4f, center = Offset(ex, ey))
            drawRect(ec, Offset(ex - ts * 0.16f, ey), Size(ps * 1.6f, ps * 1.6f))
            drawRect(ec, Offset(ex + ts * 0.08f, ey), Size(ps * 1.6f, ps * 1.6f))
        }
        for (i in 0 until 16) {
            val fx = ox + ((cellHash(i, 3) % GW) + 0.5f + kotlin.math.sin(t * 0.6f + i) * 1.4f) * ts
            val fy = oy + ((cellHash(i, 9) % GH) + 0.5f + kotlin.math.cos(t * 0.5f + i * 1.7f) * 1.2f) * ts
            val tw = (0.5f + 0.5f * kotlin.math.sin(t * 2.2f + i * 2.3f)).coerceIn(0f, 1f)
            drawCircle(Color(0xFFE8F27A).copy(alpha = 0.25f * tw * (dark / 0.55f)), radius = ts * 0.22f, center = Offset(fx, fy))
            drawCircle(Color(0xFFF8FFB0).copy(alpha = 0.9f * tw * (dark / 0.55f)), radius = ts * 0.06f, center = Offset(fx, fy))
        }
    }

    // seasons follow the real calendar: blossoms, warm light, falling leaves, snow
    val season = when (java.time.LocalDate.now().monthValue) { 12, 1, 2 -> 3; 3, 4, 5 -> 0; 6, 7, 8 -> 1; else -> 2 }
    val snowing = sim.outsideSnow || season == 3
    drawRect(
        when (season) {
            0 -> Color(0xFFFFB6D5)
            1 -> Color(0xFFFFE9A0)
            2 -> Color(0xFFFF9A3C)
            else -> Color(0xFFBFD8FF)
        }.copy(alpha = 0.06f)
    )
    val specks = when {
        snowing -> 70
        season == 2 -> 24
        season == 0 -> 18
        else -> 0
    }
    for (i in 0 until specks) {
        val h1 = (cellHash(i, 21) % 1000) / 1000f
        val h2 = (cellHash(i, 33) % 1000) / 1000f
        val py = (h1 + t * (0.03f + h2 * 0.05f)) % 1f
        val px = (h2 + kotlin.math.sin(t * 0.8f + i) * 0.03f + (if (season == 2) py * 0.15f else 0f) + 1f) % 1f
        val c = when {
            snowing -> Color(0xFFFFFFFF)
            season == 2 -> if (i % 2 == 0) Color(0xFFE0752B) else Color(0xFFC9A227)
            else -> Color(0xFFFFB6D5)
        }
        drawRect(c.copy(alpha = 0.85f), Offset(px * size.width, py * size.height), Size(ps * 1.6f, ps * 1.6f))
    }

    // world ages tint the whole land and fill the air
    val ai = sim.ageInfo()
    if (ai.tintA > 0f) drawRect(Color(ai.tint).copy(alpha = ai.tintA))
    fun motes(n: Int, c: Color, dir: Float, sz: Float, spd: Float) {
        for (i in 0 until n) {
            val h1 = (cellHash(i, 71) % 1000) / 1000f
            val h2 = (cellHash(i, 53) % 1000) / 1000f
            val py = (((h1 + t * spd * (0.4f + h2) * dir) % 1f) + 1f) % 1f
            val px = (h2 + kotlin.math.sin(t * 0.7f + i) * 0.02f + 1f) % 1f
            val tw = 0.5f + 0.5f * kotlin.math.sin(t * 3f + i)
            drawRect(c.copy(alpha = 0.85f * tw), Offset(px * size.width, py * size.height), Size(ps * sz, ps * sz))
        }
    }
    when (ai.particle) {
        "motes" -> motes(26, Color(0xFFFFF3B0), -1f, 1.4f, 0.03f)
        "silver" -> motes(34, Color(0xFFDDE6FF), -1f, 1.6f, 0.025f)
        "sparkle" -> {
            motes(18, Color(0xFFFF9AF0), -1f, 1.8f, 0.04f)
            motes(18, Color(0xFF9AF0FF), -1f, 1.8f, 0.03f)
            motes(12, Color(0xFFFFF59A), -1f, 1.8f, 0.05f)
        }
        "embers" -> {
            motes(34, Color(0xFFFF7A2B), -1f, 1.6f, 0.06f)
            if (kotlin.math.sin(t * 7f) * kotlin.math.sin(t * 3.1f) > 0.93f) drawRect(Color(0xFFFF3A2A).copy(alpha = 0.12f))
        }
        "ash" -> {
            motes(60, Color(0xFFB8B8C0), 1f, 1.7f, 0.045f)
            motes(14, Color(0xFFFF7A2B), 1f, 1.3f, 0.04f)
        }
        "wisps" -> motes(24, Color(0xFF7A5AB8), -1f, 2.4f, 0.02f)
        "rays" -> {
            for (k in 0 until 4) {
                val sx = size.width * (0.1f + 0.25f * k) + kotlin.math.sin(t * 0.2f + k) * ts
                drawLine(
                    Color(0xFFFFF0A0).copy(alpha = 0.07f + 0.03f * kotlin.math.sin(t * 0.8f + k)),
                    Offset(sx, 0f), Offset(sx - size.width * 0.18f, size.height), strokeWidth = ts * 1.6f,
                )
            }
        }
        "fog" -> {
            for (k in 0 until 5) {
                val fxp = ((t * 0.01f + k * 0.23f) % 1.3f - 0.15f) * size.width
                drawOval(
                    Color(0xFFB0B0BC).copy(alpha = 0.11f),
                    Offset(fxp - ts * 6f, size.height * (0.15f + 0.18f * k) - ts), Size(ts * 12f, ts * 3.4f),
                )
            }
        }
    }

    // rain
    if (sim.raining()) {
        drawRect(Color(0xFF203050).copy(alpha = 0.16f))
        for (i in 0 until 90) {
            val rx = ((cellHash(i, 5) % 1000) / 1000f) * size.width
            val ry = ((((cellHash(i, 11) % 1000) / 1000f) * size.height + t * ts * 14f) % size.height)
            drawLine(
                Color(0xFFB8D4F0).copy(alpha = 0.5f),
                Offset(rx, ry),
                Offset(rx - ts * 0.15f, ry + ts * 0.5f),
                strokeWidth = max(1f, ps),
            )
        }
    }

    // soft vignette
    drawRect(
        Brush.radialGradient(
            listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = max(size.width, size.height) * 0.72f,
        )
    )

    ageBanner(sim, geo, namePaint, bubblePaint)

    // speech bubbles and names
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        if (labels) {
            for (k in sim.kingdoms) {
                if (k.fallen) continue
                val lx = ox + sim.kx(k) * ts
                val ly = oy + (sim.ky(k) - (if (k.id == 0) 1.25f else 1.35f)) * ts
                namePaint.textSize = ts * 0.36f
                namePaint.textAlign = Paint.Align.CENTER
                namePaint.color = k.color.toInt()
                nc.drawText(k.name + " · " + k.pop, lx, ly, namePaint)
            }
            namePaint.color = android.graphics.Color.argb(230, 255, 255, 255)
        }
        for (v in sorted) {
            val cx = ox + v.x * ts
            val top = oy + (v.y + 0.45f) * ts - 12f * ps
            if (labels) {
                namePaint.textSize = ts * 0.34f
                namePaint.textAlign = Paint.Align.CENTER
                nc.drawText(v.name, cx, oy + (v.y + 0.45f) * ts + ts * 0.42f, namePaint)
            }
            if (v.say.isNotEmpty()) {
                textPaint.textSize = ts * 0.36f
                val txt = if (v.say.length > 46) v.say.take(45) + "…" else v.say
                val tw = textPaint.measureText(txt)
                val r = RectF(cx - tw / 2f - ts * 0.25f, top - ts * 0.85f, cx + tw / 2f + ts * 0.25f, top - ts * 0.15f)
                nc.drawRoundRect(r, ts * 0.2f, ts * 0.2f, bubblePaint)
                val tail = android.graphics.Path().apply {
                    moveTo(cx - ts * 0.12f, r.bottom - 1f)
                    lineTo(cx + ts * 0.12f, r.bottom - 1f)
                    lineTo(cx, r.bottom + ts * 0.18f)
                    close()
                }
                nc.drawPath(tail, bubblePaint)
                textPaint.textAlign = Paint.Align.CENTER
                nc.drawText(txt, cx, r.centerY() + ts * 0.13f, textPaint)
            }
        }
    }
}

/** The full-screen village: watch, read what everyone is thinking, and speak to the village. */
