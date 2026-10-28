package com.jondude1.glasswall.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import com.jondude1.glasswall.sim.Building
import com.jondude1.glasswall.sim.CivSim
import com.jondude1.glasswall.sim.DAY_LEN
import com.jondude1.glasswall.sim.GH
import com.jondude1.glasswall.sim.GW
import com.jondude1.glasswall.sim.T
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/** Where things are on screen this frame. ts is one tile, ps is one pixel of a small sprite. */
class Geo(val ox: Float, val oy: Float, val ts: Float, val ps: Float, val t: Float, val dark: Float)

/** The sun for this moment: which way shadows fall, how long they are and how dark. */
class Sun(val shear: Float, val len: Float, val alpha: Float)

fun sunFor(sim: CivSim, dark: Float): Sun {
    val f = (sim.time % DAY_LEN) / DAY_LEN
    val u = ((f - 0.05) / 0.62).toFloat().coerceIn(0f, 1f)
    val shear = (u - 0.5f) * 2.2f
    val len = 0.30f + 0.32f * abs(2f * u - 1f)
    var a = 0.28f * (1f - dark / 0.55f)
    if (sim.raining()) a *= 0.35f
    return Sun(shear, len, a.coerceIn(0f, 0.28f))
}

/** A flattened, sheared black copy of a sprite, so everything throws a shadow away from the sun. */
internal fun DrawScope.castShadow(art: CivArt, sp: ImageBitmap, cx: Float, baseY: Float, w: Float, h: Float, sun: Sun) {
    if (sun.alpha < 0.02f) return
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        nc.save()
        val m = android.graphics.Matrix()
        m.setValues(floatArrayOf(1f, -sun.shear, sun.shear * baseY, 0f, -sun.len, baseY * (1f + sun.len), 0f, 0f, 1f))
        nc.concat(m)
        art.shadowPaint.alpha = (sun.alpha * 255f).toInt()
        nc.drawBitmap(art.shadowOf(sp), null, android.graphics.RectF(cx - w / 2f, baseY - h, cx + w / 2f, baseY), art.shadowPaint)
        nc.restore()
    }
}

private fun hsh(a: Int, b: Int, c: Int): Int = PixelFx.hash(a, b, c)

private fun DrawScope.icon(rows: List<String>, pal: Map<Char, Long>, x: Float, y: Float, u: Float) {
    for (ry in rows.indices) {
        val row = rows[ry]
        for (cx in row.indices) {
            val c = pal[row[cx]] ?: continue
            drawRect(Color(c), Offset(x + cx * u, y + ry * u), Size(u + 0.5f, u + 0.5f))
        }
    }
}

private val ICON_HEART = listOf(".h.h.", "hhhhh", "hhhhh", ".hhh.", "..h..")
private val ICON_EXCL = listOf("..e..", "..e..", "..e..", ".....", "..e..")
private val ICON_QUEST = listOf(".qqq.", "q...q", "...q.", ".....", "..q..")
private val ICON_NOTE = listOf("..nnn", "..n.n", "..n..", "nnn..", "nn...")
private val ICON_SWEAT = listOf("..s..", ".sss.", "sssss", "sssss", ".sss.")
private val ICON_FOOD = listOf("..g..", "..b..", ".rrr.", "rrrrr", ".rrr.")
private val ICON_Z = listOf("zzzzz", "...z.", "..z..", ".z...", "zzzzz")

private fun emoteFor(sim: CivSim, v: com.jondude1.glasswall.sim.Villager, t: Float): String? {
    if (v.say.isNotEmpty()) return null
    val mood = sim.mood(v)
    val cyc = (t * 0.45f + v.id * 0.37f) % 1f
    return when {
        mood == "scared" -> "excl"
        mood == "starving" -> "food"
        mood == "exhausted" -> "sweat"
        v.waiting -> if (cyc < 0.7f) "quest" else null
        v.talkTo != null && mood == "joyful" -> if (cyc < 0.7f) "heart" else null
        v.job == "Storyteller" && (v.phase == "idle" || v.talkTo != null) -> if (cyc < 0.75f) "note" else null
        v.phase == "idle" && v.idleFor > 5f -> if (cyc < 0.6f) "dots" else null
        else -> null
    }
}

private fun resourceColor(action: String): Long = when (action) {
    "gather_wood" -> 0xFFB07A3CL
    "gather_stone" -> 0xFFB8BEC8L
    "mine_ore" -> 0xFFD98A3DL
    "smelt" -> 0xFFFF9A2BL
    "tend_farm" -> 0xFFE8C547L
    else -> 0xFFE0405AL
}

// ---------------------------------------------------------------------------------------------
// Things on the ground: grass that sways, lilies, reeds and rain rings. Drawn before anything stands.
// ---------------------------------------------------------------------------------------------
fun DrawScope.lifeUnderlay(sim: CivSim, art: CivArt, g: Geo) {
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    for (i in art.grassCells) {
        if (sim.tiles[i] != T.GRASS) continue
        val h = hsh(i, 7, 5)
        if (h % 3 != 0) continue
        val x = i % GW
        val y = i / GW
        val bx = g.ox + (x + 0.25f + ((h shr 4) % 50) / 100f) * ts
        val by = g.oy + (y + 0.55f + ((h shr 9) % 40) / 100f) * ts
        val sway = sin(t * 1.7f + x * 0.45f + y * 0.3f + (h % 7)) * ps * 1.1f
        val col = if (h % 2 == 0) Color(0xFF3F8744) else Color(0xFF86CC6E)
        drawLine(col, Offset(bx, by), Offset(bx + sway, by - ps * 3.2f), strokeWidth = ps * 0.9f)
        drawLine(col, Offset(bx + ps * 1.3f, by), Offset(bx + ps * 1.3f + sway * 0.8f, by - ps * 2.4f), strokeWidth = ps * 0.9f)
    }
    for (i in art.shoreWater) {
        val h = hsh(i, 9, 6)
        val x = i % GW
        val y = i / GW
        if (h % 4 == 0) {
            // a lily pad with a flower
            val lx = g.ox + (x + 0.3f + ((h shr 3) % 40) / 100f) * ts
            val ly = g.oy + (y + 0.35f + ((h shr 8) % 30) / 100f) * ts + sin(t * 1.5f + h % 5) * ps * 0.4f
            drawOval(Color(0xFF3F9A4E), Offset(lx - ps * 2.2f, ly - ps * 1.2f), Size(ps * 4.4f, ps * 2.4f))
            drawOval(Color(0xFF2E7A3C), Offset(lx - ps * 1.2f, ly - ps * 0.6f), Size(ps * 2.4f, ps * 1.2f))
            if (h % 8 == 0) drawRect(Color(0xFFFF9FC8), Offset(lx - ps * 0.5f, ly - ps * 1.8f), Size(ps * 1.3f, ps * 1.3f))
        } else if (h % 4 == 1) {
            // reeds with brown heads
            val rx = g.ox + (x + 0.2f + ((h shr 3) % 60) / 100f) * ts
            val ry = g.oy + (y + 0.7f) * ts
            for (k in 0 until 3) {
                val sw = sin(t * 1.4f + k + (h % 5)) * ps * 0.9f
                val top = Offset(rx + k * ps * 1.6f + sw, ry - ps * (5f + k % 2 * 1.5f))
                drawLine(Color(0xFF5C8A3A), Offset(rx + k * ps * 1.6f, ry), top, strokeWidth = ps * 0.8f)
                drawRect(Color(0xFF6B4A2B), Offset(top.x - ps * 0.5f, top.y - ps * 0.2f), Size(ps * 1.2f, ps * 2.2f))
            }
        }
    }
    if (sim.raining() && art.innerWater.isNotEmpty()) {
        for (k in 0 until 9) {
            val cell = art.innerWater[hsh(k, 41, 7) % art.innerWater.size]
            val p = (t * 0.9f + k * 0.37f) % 1f
            val cx = g.ox + (cell % GW + 0.2f + ((hsh(k, 42, 7) % 60) / 100f)) * ts
            val cy = g.oy + (cell / GW + 0.2f + ((hsh(k, 43, 7) % 60) / 100f)) * ts
            val r = ps * (1f + 5f * p)
            drawOval(Color.White.copy(alpha = 0.55f * (1f - p)), Offset(cx - r, cy - r * 0.45f), Size(r * 2f, r * 0.9f), style = Stroke(width = ps * 0.8f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Crops that grow with the farm.
// ---------------------------------------------------------------------------------------------
fun DrawScope.crops(b: Building, g: Geo, left: Float, top: Float) {
    val u = g.ps
    val prog = (b.ready + (b.timer / 40f).coerceIn(0f, 0.99f)).coerceIn(0f, 3f)
    val ripe = prog / 3f
    val hp = 0.3f + prog * 0.9f
    val green = Color(0xFF6DBE45)
    val gold = Color(0xFFE8C547)
    val stalk = Color(
        green.red + (gold.red - green.red) * ripe,
        green.green + (gold.green - green.green) * ripe,
        green.blue + (gold.blue - green.blue) * ripe,
    )
    for (row in 0 until 4) {
        val baseY = top + (3 + row * 2) * u
        for (k in 0 until 6) {
            val bx = left + (1.5f + k * 2f) * u
            val sw = sin(g.t * 2f + k * 0.9f + row) * u * 0.35f * (0.4f + ripe)
            drawRect(stalk, Offset(bx + sw, baseY - hp * u), Size(u * 0.9f, hp * u))
            if (prog >= 2f) {
                drawRect(gold, Offset(bx + sw - u * 0.35f, baseY - hp * u - u * 0.5f), Size(u * 1.6f, u * 1.3f))
            } else if (prog < 1f) {
                drawRect(Color(0xFF9BE070), Offset(bx, baseY - hp * u - u * 0.3f), Size(u * 0.9f, u * 0.9f))
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Villagers: they face where they go, lean into their steps, breathe, hop when they are small,
// carry what they gathered, show how they feel, kick up dust and throw chips when they work.
// ---------------------------------------------------------------------------------------------
fun DrawScope.drawVillagers(sim: CivSim, art: CivArt, g: Geo, sun: Sun) {
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    val sorted = sim.villagers.sortedBy { it.y }
    for (v in sorted) {
        val child = sim.isChild(v)
        val kid = if (child) 0.72f else 1f
        val walking = v.phase == "walking"
        val working = v.phase == "working"
        val prevG = art.lastGather[v.id] ?: v.gathered
        if (v.gathered > prevG && art.floaters.size < 40) {
            art.floaters.add(Floater(v.x, v.y - 0.9f, sim.time, resourceColor(v.action).toInt()))
        }
        art.lastGather[v.id] = v.gathered

        val cx = g.ox + v.x * ts
        val feetY = g.oy + (v.y + 0.45f) * ts
        if (v.phase == "sleeping") {
            // little z's float up from the sleeper
            for (k in 0 until 3) {
                val p = (t * 0.45f + k * 0.34f + v.id * 0.13f) % 1f
                val zx = cx + ps * (2f + k * 1.5f) + sin(t * 1.3f + k) * ps
                val zy = feetY - ps * (8f + p * 12f)
                val zu = ps * (0.45f + 0.15f * k)
                val a = if (p > 0.7f) (1f - p) / 0.3f else min(1f, p * 4f)
                icon(ICON_Z, mapOf('z' to 0xFFEAF0FFL), zx, zy, zu * (0.6f + a * 0.4f))
            }
            continue
        }
        val sp = art.person(v.colorIdx, if (walking) (((v.x + v.y) * 3f).toInt() and 1) else 0)
        val w = sp.lw * ps * kid
        val h = sp.lh * ps * kid
        val tt = v.talkTo
        var fx = if (v.facing < 0) -1f else 1f
        if (tt != null) {
            fx = if (tt.x >= v.x) 1f else -1f
        } else if (v.phase == "idle" && sin(t * 0.55f + v.id * 2.3f) > 0.985f) {
            fx = -fx
        }
        val talking = tt != null || v.say.isNotEmpty()
        val bob = when {
            walking -> abs(sin(t * 9f + v.id)) * ps * 0.9f * kid
            working -> abs(sin(t * 6f + v.id)) * ps * 1.2f * kid
            child -> abs(sin(t * 5f + v.id)) * ps * 1.4f * kid
            talking -> abs(sin(t * 4f + v.id)) * ps * 0.6f
            else -> 0f
        }
        val breathe = if (walking || working) 1f else 1f + 0.03f * sin(t * 2.2f + v.id)
        val lean = when {
            walking -> 4f
            working -> 6f * sin(t * 6f + v.id)
            else -> 0f
        }

        castShadow(art, sp, cx, feetY, w, h, sun)
        drawOval(Color.Black.copy(alpha = 0.22f), Offset(cx - w * 0.45f, feetY - ps * 1.3f), Size(w * 0.9f, ps * 2.6f))

        // dust kicked up by walking feet
        if (walking) {
            for (k in 0 until 2) {
                val p = (t * 3f + k * 0.5f + v.id * 0.3f) % 1f
                drawCircle(
                    Color(0xFFD9CDB0).copy(alpha = 0.30f * (1f - p)),
                    radius = ps * (0.8f + 1.4f * p),
                    center = Offset(cx - fx * ps * (2f + p * 5f), feetY - p * ps * 3f),
                )
            }
        }

        val top = feetY - h - bob
        withTransform({
            rotate(lean * fx, Offset(cx, feetY))
            scale(fx, breathe, Offset(cx, feetY))
        }) {
            img(sp, cx - w / 2f, top, w, h)
            if (!child) jobGear(v.job, cx - w / 2f, top, w / 8f, working, t + v.id)
            // what they carry on their back
            if (v.gathered > 0 && !child) {
                val u = w / 8f
                fun r(c: Long, x: Float, y: Float, rw: Float, rh: Float) =
                    drawRect(Color(c), Offset(cx - w / 2f + x * u, top + y * u), Size(rw * u, rh * u))
                when (v.job) {
                    "Woodcutter" -> {
                        r(0xFF7A4B24, -2.8f, 3.2f, 3.2f, 1.3f)
                        r(0xFF8F5A2C, -2.6f, 4.6f, 3.2f, 1.3f)
                        r(0xFFD9B77E, -2.9f, 3.3f, 0.6f, 1.1f)
                    }
                    "Miner" -> {
                        r(0xFF9AA0AA, -2.6f, 5f, 2.6f, 2.4f)
                        r(0xFFD98A3D, -1.6f, 5.4f, 0.9f, 0.9f)
                    }
                    "Forager" -> {
                        r(0xFF4E9E4A, -2.4f, 4.4f, 2.4f, 1.6f)
                        r(0xFFE0405A, -1.8f, 4.0f, 0.9f, 0.9f)
                        r(0xFFE0405A, -0.9f, 4.6f, 0.9f, 0.9f)
                    }
                    "Farmer" -> {
                        r(0xFFE8C547, -2.4f, 3.2f, 2.2f, 3.6f)
                        r(0xFFC9A227, -2.4f, 6.2f, 2.2f, 0.6f)
                    }
                    else -> r(0xFF8B6B45, -2.4f, 4.4f, 2.4f, 2.2f)
                }
            }
        }

        // what they are doing right now
        if (working) {
            val act = v.action
            val chips = act.startsWith("gather_") || act == "mine_ore" || act == "smelt" || act == "tend_farm" || act.startsWith("build")
            if (chips) {
                val col = Color(resourceColor(if (act.startsWith("build")) "gather_wood" else act))
                for (k in 0 until 5) {
                    val p = (t * 1.7f + k * 0.2f + v.id * 0.17f) % 1f
                    val dx = fx * ps * 3f + (k - 2f) * ps * 2f * p
                    val dy = -(p * (1.1f - p)) * ps * 16f
                    val s = ps * (if (act == "tend_farm") 1.1f else 1.5f)
                    val c = if (act == "tend_farm") Color(0xFF7FC4F0) else if (act == "smelt" && k % 2 == 0) Color(0xFFFFE070) else col
                    drawRect(c.copy(alpha = 1f - p * 0.8f), Offset(cx + dx, feetY - h * 0.6f + dy), Size(s, s))
                }
                if (act != "tend_farm" && sin(t * 6f + v.id) > 0.92f) {
                    drawCircle(Color.White.copy(alpha = 0.5f), radius = ps * 2.4f, center = Offset(cx + fx * ps * 5f, feetY - h * 0.45f))
                }
            }
        }

        // how they feel, over their head
        val em = emoteFor(sim, v, t)
        if (em != null) {
            val u = ps * 1.15f
            val ex = cx - u * 2.5f
            val shake = if (em == "excl") sin(t * 30f) * ps * 0.5f else 0f
            val ey = feetY - h - bob - ps * (6f + sin(t * 3f + v.id) * 0.8f)
            when (em) {
                "heart" -> icon(ICON_HEART, mapOf('h' to 0xFFFF5A7AL), ex, ey, u)
                "excl" -> icon(ICON_EXCL, mapOf('e' to 0xFFFF4040L), ex + shake, ey, u)
                "quest" -> icon(ICON_QUEST, mapOf('q' to 0xFFFFE066L), ex, ey, u)
                "note" -> icon(ICON_NOTE, mapOf('n' to 0xFFC77DFFL), ex, ey, u)
                "sweat" -> icon(ICON_SWEAT, mapOf('s' to 0xFF7FC4F0L), ex, ey, u)
                "food" -> icon(ICON_FOOD, mapOf('g' to 0xFF4E9E4AL, 'b' to 0xFF6B4A2BL, 'r' to 0xFFE0405AL), ex, ey, u)
                else -> for (k in 0 until 3) {
                    val on = ((t * 3f + k).toInt() and 3) != 0
                    if (on) drawRect(Color.White.copy(alpha = 0.9f), Offset(ex + k * u * 2f, ey + u * 3f), Size(u * 1.2f, u * 1.2f))
                }
            }
        }
    }

    // "+1" rising from where something was gathered
    val iter = art.floaters.iterator()
    while (iter.hasNext()) {
        val f = iter.next()
        val age = (sim.time - f.born).toFloat()
        if (age > 1.4f) {
            iter.remove()
            continue
        }
        val p = age / 1.4f
        val x = g.ox + f.x * ts
        val y = g.oy + f.y * ts - p * ts * 1.1f
        val a = 1f - p
        val white = Color.White.copy(alpha = a)
        drawRect(white, Offset(x - ps * 3.2f, y - ps * 0.5f), Size(ps * 2.4f, ps * 0.9f))
        drawRect(white, Offset(x - ps * 2.45f, y - ps * 1.25f), Size(ps * 0.9f, ps * 2.4f))
        drawRect(Color(f.color).copy(alpha = a), Offset(x, y - ps * 1.4f), Size(ps * 2.8f, ps * 2.8f))
    }
}

// ---------------------------------------------------------------------------------------------
// Wildlife and weather: ducks, jumping fish, rabbits, dragonflies, chimney smoke, rain splashes.
// ---------------------------------------------------------------------------------------------
private fun rabbitX(hx: Float, k: Int, n: Int): Float = hx + (PixelFx.vnoise(n * 0.55f + k * 9f, 1.3f, 5) - 0.5f) * 3.4f
private fun rabbitY(hy: Float, k: Int, n: Int): Float = hy + (PixelFx.vnoise(n * 0.55f + k * 9f, 7.7f, 6) - 0.5f) * 2.4f

fun DrawScope.lifeCritters(sim: CivSim, art: CivArt, g: Geo) {
    val ts = g.ts
    val ps = g.ps
    val t = g.t
    val day = g.dark < 0.35f

    // ducks paddling on open water, with a little wake
    if (day && art.innerWater.isNotEmpty()) {
        for (k in 0 until 3) {
            val cell = art.innerWater[hsh(k, 11, 3) % art.innerWater.size]
            val a = t * (0.28f + 0.05f * k) * (if (k % 2 == 0) 1f else -1f) + k * 2f
            val x = cell % GW + 0.5f + cos(a) * 0.55f
            val y = cell / GW + 0.5f + sin(a) * 0.32f
            val dirX = if (k % 2 == 0) -sin(a) else sin(a)
            val dir = if (dirX >= 0f) 1f else -1f
            val sp = art.duck[((t * 1.6f + k).toInt()) and 1]
            val w = sp.lw * ps
            val h = sp.lh * ps
            val px = g.ox + x * ts
            val py = g.oy + y * ts + sin(t * 3f + k) * ps * 0.4f
            drawLine(Color.White.copy(alpha = 0.35f), Offset(px - dir * ps * 3f, py + ps * 0.4f), Offset(px - dir * ps * 8f, py - ps * 0.8f), strokeWidth = ps * 0.8f)
            drawLine(Color.White.copy(alpha = 0.35f), Offset(px - dir * ps * 3f, py + ps * 0.4f), Offset(px - dir * ps * 8f, py + ps * 1.8f), strokeWidth = ps * 0.8f)
            withTransform({ scale(dir, 1f, Offset(px, py)) }) {
                img(sp, px - w / 2f, py - h, w, h)
            }
        }
    }

    // fish leaping out of the water
    val pool = if (art.innerWater.isNotEmpty()) art.innerWater else art.shoreWater
    if (pool.isNotEmpty() && g.dark < 0.5f) {
        for (k in 0 until 4) {
            val cell = pool[hsh(k, 21, 4) % pool.size]
            val period = 7f + (hsh(k, 22, 4) % 60) / 10f
            val u = (t + k * 3.1f) % period
            if (u < 1.1f) {
                val p = u / 1.1f
                val wx = g.ox + (cell % GW + 0.5f + (p - 0.5f) * 0.8f) * ts
                val surface = g.oy + (cell / GW + 0.62f) * ts
                val wy = surface - sin(p * PI.toFloat()) * ts * 0.6f
                val rise = if (p < 0.5f) 1f else -1f
                drawRect(Color(0xFFC8D6E5), Offset(wx - ps * 2f, wy - ps * 0.7f), Size(ps * 4f, ps * 1.5f))
                drawRect(Color(0xFFEAF2FA), Offset(wx - ps * 1.2f, wy + ps * 0.2f), Size(ps * 2.4f, ps * 0.6f))
                drawRect(Color(0xFF8FA6BC), Offset(wx - ps * 3.2f, wy - ps * 1.4f * rise - ps * 0.3f), Size(ps * 1.4f, ps * 2.2f))
                drawRect(Color(0xFF222222), Offset(wx + ps * 1.2f, wy - ps * 0.5f), Size(ps * 0.7f, ps * 0.7f))
                val splash = if (p < 0.2f) p / 0.2f else if (p > 0.8f) (1f - p) / 0.2f else 0f
                if (splash > 0f) {
                    val sx = g.ox + (cell % GW + 0.5f + (if (p < 0.5f) -0.4f else 0.4f)) * ts
                    val r = ps * (1f + 5f * (1f - splash))
                    drawOval(Color.White.copy(alpha = 0.7f * splash), Offset(sx - r, surface - r * 0.45f), Size(r * 2f, r * 0.9f), style = Stroke(width = ps * 0.8f))
                }
            }
        }
    }

    // rabbits hopping about the meadows
    if (g.dark < 0.45f && art.meadowCells.isNotEmpty()) {
        for (k in 0 until 3) {
            val cell = art.meadowCells[hsh(k, 31, 6) % art.meadowCells.size]
            val hx = cell % GW + 0.5f
            val hy = cell / GW + 0.5f
            val period = 1.9f + k * 0.4f
            val n = floor(t / period).toInt()
            val u = t / period - n
            val hop = PixelFx.smooth(min(1f, u / 0.35f))
            val x0 = rabbitX(hx, k, n)
            val y0 = rabbitY(hy, k, n)
            val x1 = rabbitX(hx, k, n + 1)
            val y1 = rabbitY(hy, k, n + 1)
            val x = x0 + (x1 - x0) * hop
            val y = y0 + (y1 - y0) * hop
            val air = if (u < 0.35f) sin(hop * PI.toFloat()) * ts * 0.32f else 0f
            val sp = art.rabbit[if (u < 0.35f) 1 else 0]
            val w = sp.lw * ps
            val h = sp.lh * ps
            val px = g.ox + x * ts
            val py = g.oy + y * ts
            val dir = if (x1 >= x0) 1f else -1f
            drawOval(Color.Black.copy(alpha = 0.2f), Offset(px - w * 0.4f, py - ps * 0.8f), Size(w * 0.8f, ps * 1.8f))
            withTransform({ scale(dir, 1f, Offset(px, py)) }) {
                img(sp, px - w / 2f, py - h - air, w, h)
            }
        }
    }

    // dragonflies darting over the shore on warm days
    if (g.dark < 0.3f && !sim.raining() && art.shoreWater.isNotEmpty()) {
        for (k in 0 until 3) {
            val cell = art.shoreWater[hsh(k, 51, 8) % art.shoreWater.size]
            val x = cell % GW + 0.5f + sin(t * 1.9f + k * 2f) * 0.9f + sin(t * 5.3f + k) * 0.15f
            val y = cell / GW + 0.2f + cos(t * 1.3f + k) * 0.5f
            val px = g.ox + x * ts
            val py = g.oy + y * ts
            val flick = 0.5f + 0.5f * sin(t * 40f + k)
            drawRect(Color(0xFF2FA8C8), Offset(px - ps * 1.5f, py), Size(ps * 3f, ps * 0.9f))
            drawRect(Color.White.copy(alpha = 0.3f + 0.5f * flick), Offset(px - ps * 0.5f, py - ps * 1.4f), Size(ps * 0.9f, ps * 1.4f))
            drawRect(Color.White.copy(alpha = 0.8f - 0.5f * flick), Offset(px - ps * 0.5f, py + ps * 0.9f), Size(ps * 0.9f, ps * 1.4f))
        }
    }

    // chimney smoke from the houses when the day is cool or the light is going
    if (g.dark > 0.04f) {
        for (b in sim.buildings) {
            if (b.type != "hut" || b.era < 1) continue
            val bx = g.ox + (b.x + 0.5f) * ts + ps * 3.6f
            val by = g.oy + (b.y + 0.97f) * ts - ps * 10.5f
            for (k in 0 until 4) {
                val ph = (t * 0.35f + k * 0.25f + b.x * 0.11f) % 1f
                val rise = ph * ts * 1.5f
                val sway = sin(t * 1.1f + k * 2f + b.y) * ts * 0.14f * ph
                drawCircle(
                    Color(0xFFD8D4D0).copy(alpha = 0.30f * (1f - ph)),
                    radius = ts * (0.07f + 0.13f * ph),
                    center = Offset(bx + sway + ph * ts * 0.25f, by - rise),
                )
            }
        }
    }

    // rain bouncing off the ground
    if (sim.raining()) {
        for (i in 0 until 16) {
            val p = (t * 2.2f + i * 0.31f) % 1f
            val sx = g.ox + ((hsh(i, 61, 9) % 1000) / 1000f) * GW * ts
            val sy = g.oy + ((hsh(i, 62, 9) % 1000) / 1000f) * GH * ts
            val r = ps * (0.6f + 2.2f * p)
            drawOval(Color(0xFFDCE8F8).copy(alpha = 0.45f * (1f - p)), Offset(sx - r, sy - r * 0.45f), Size(r * 2f, r * 0.9f), style = Stroke(width = ps * 0.7f))
        }
    }
}


// ---------------------------------------------------------------------------------------------
// What the beasts leave behind: a haunch of meat and a rolled-up hide, until somebody hauls it home.
// ---------------------------------------------------------------------------------------------
fun DrawScope.drawDrops(sim: CivSim, g: Geo) {
    val ps = g.ps
    for (d in sim.drops) {
        val age = (sim.time - d.born).toFloat()
        val pop = if (age < 0.5f) sin(age / 0.5f * PI.toFloat()) * g.ts * 0.7f else 0f
        val x = g.ox + d.x * g.ts
        val y = g.oy + d.y * g.ts
        val bob = sin(g.t * 3f + d.x) * ps * 0.5f
        val fade = if (age > 24f) ((30f - age) / 6f).coerceIn(0.3f, 1f) else 1f
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(x - ps * 4f, y - ps * 0.8f), Size(ps * 8f, ps * 2.2f))
        val top = y - ps * 4f - pop + bob
        // the hide, rolled up
        drawRect(Color(0xFF7A5A3A).copy(alpha = fade), Offset(x - ps * 4.2f, top + ps * 1.4f), Size(ps * 4f, ps * 2.6f))
        drawRect(Color(0xFF9A7650).copy(alpha = fade), Offset(x - ps * 4.2f, top + ps * 1.4f), Size(ps * 4f, ps * 0.9f))
        // the meat on the bone
        drawRect(Color(0xFFEFE6D0).copy(alpha = fade), Offset(x + ps * 0.4f, top + ps * 0.4f), Size(ps * 3.6f, ps * 0.9f))
        drawRect(Color(0xFFC93C4A).copy(alpha = fade), Offset(x - ps * 0.2f, top + ps * 1.0f), Size(ps * 3.8f, ps * 2.8f))
        drawRect(Color(0xFFE5707A).copy(alpha = fade), Offset(x - ps * 0.2f, top + ps * 1.0f), Size(ps * 3.8f, ps * 0.9f))
        // a little glint so the eye finds it
        val tw = (0.5f + 0.5f * sin(g.t * 5f + d.y * 3f)).coerceIn(0f, 1f)
        drawRect(Color.White.copy(alpha = 0.8f * tw), Offset(x + ps * 3f, top - ps * 0.6f), Size(ps * 1.1f, ps * 1.1f))
    }
}

// ---------------------------------------------------------------------------------------------
// A new age: a gold ring spreads from the village and a banner names the age.
// ---------------------------------------------------------------------------------------------
fun DrawScope.ageBanner(sim: CivSim, g: Geo, namePaint: android.graphics.Paint, bubblePaint: android.graphics.Paint) {
    val age = (sim.time - sim.ageBannerAt).toFloat()
    if (age < 0f || age > 9f || sim.ageBannerName.isEmpty()) return
    val a = when {
        age < 0.8f -> age / 0.8f
        age > 7f -> ((9f - age) / 2f).coerceIn(0f, 1f)
        else -> 1f
    }
    val cx = g.ox + com.jondude1.glasswall.sim.GW * g.ts / 2f
    val cy = g.oy + com.jondude1.glasswall.sim.GH * g.ts / 2f
    val p = (age / 3f).coerceIn(0f, 1f)
    if (p < 1f) {
        drawCircle(
            Color(0xFFFFE08A).copy(alpha = 0.55f * (1f - p)),
            radius = g.ts * (2f + p * 26f), center = Offset(cx, cy),
            style = Stroke(width = g.ps * (3f + 5f * (1f - p))),
        )
        drawCircle(
            Color(0xFFFFF3C8).copy(alpha = 0.25f * (1f - p)),
            radius = g.ts * (1f + p * 18f), center = Offset(cx, cy),
            style = Stroke(width = g.ps * 2f),
        )
    }
    val w = min(size.width * 0.8f, g.ts * 20f)
    val h = g.ts * 2.6f
    val left = (size.width - w) / 2f
    val top = size.height * 0.16f - (1f - a) * g.ts
    drawRect(Color(0xFF1A1424).copy(alpha = 0.72f * a), Offset(left, top), Size(w, h))
    drawRect(Color(0xFFE8C45A).copy(alpha = a), Offset(left, top), Size(w, g.ps * 1.4f))
    drawRect(Color(0xFFE8C45A).copy(alpha = a), Offset(left, top + h - g.ps * 1.4f), Size(w, g.ps * 1.4f))
    drawIntoCanvas { c ->
        val nc = c.nativeCanvas
        namePaint.textAlign = android.graphics.Paint.Align.CENTER
        namePaint.color = android.graphics.Color.argb((a * 255).toInt(), 255, 226, 140)
        namePaint.textSize = g.ts * 1.15f
        nc.drawText(sim.ageBannerName.uppercase(), size.width / 2f, top + h * 0.56f, namePaint)
        namePaint.color = android.graphics.Color.argb((a * 220).toInt(), 255, 255, 255)
        namePaint.textSize = g.ts * 0.5f
        nc.drawText("A new age begins", size.width / 2f, top + h * 0.86f, namePaint)
        namePaint.color = android.graphics.Color.argb(230, 255, 255, 255)
    }
}
