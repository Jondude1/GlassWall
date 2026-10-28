package com.jondude1.glasswall.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.util.Random

/** A sprite is drawn on a small grid, then polished to twice that size, so logical size is half the bitmap. */
val ImageBitmap.lw: Float get() = width / 2f
val ImageBitmap.lh: Float get() = height / 2f

/** A little "+1" that floats up from a villager who just gathered something. */
class Floater(val x: Float, val y: Float, val born: Double, val color: Int)

/** All the pixel art for the village, drawn from small text grids when the app starts. */
class CivArt {
    private fun sprite(rows: List<String>, pal: Map<Char, Int>, outline: Boolean = true, shade: Boolean = true): ImageBitmap {
        val h = rows.size
        var w = 1
        for (r in rows) if (r.length > w) w = r.length
        val src = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            src[y * w + x] = pal[rows[y].getOrElse(x) { '.' }] ?: 0
        }
        val big = PixelFx.polish(src, w, h, outline, shade)
        val bmp = Bitmap.createBitmap(w * 2, h * 2, Bitmap.Config.ARGB_8888)
        bmp.setPixels(big, 0, w * 2, 0, 0, w * 2, h * 2)
        return bmp.asImageBitmap()
    }

    // ---- ground, built in the background from the shape of the land ----
    @Volatile var ground: List<ImageBitmap>? = null
    @Volatile var groundSig = 0
    @Volatile var groundBusy = false

    /** Cells the life layer uses: open water, shoreline water, and plain grass. */
    var innerWater = IntArray(0)
    var shoreWater = IntArray(0)
    var grassCells = IntArray(0)
    var meadowCells = IntArray(0)

    fun groundImages(frames: Array<IntArray>, w: Int, h: Int): List<ImageBitmap> = frames.map {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(it, 0, w, 0, 0, w, h)
        bmp.asImageBitmap()
    }

    fun rebuildDecor(cw: Int, ch: Int, water: BooleanArray, grass: BooleanArray) {
        fun w(x: Int, y: Int) = x in 0 until cw && y in 0 until ch && water[y * cw + x]
        val inner = ArrayList<Int>()
        val shore = ArrayList<Int>()
        val gr = ArrayList<Int>()
        val meadow = ArrayList<Int>()
        for (y in 0 until ch) for (x in 0 until cw) {
            val i = y * cw + x
            if (water[i]) {
                var n = 0
                if (w(x - 1, y)) n++
                if (w(x + 1, y)) n++
                if (w(x, y - 1)) n++
                if (w(x, y + 1)) n++
                if (n == 4 && w(x - 1, y - 1) && w(x + 1, y - 1) && w(x - 1, y + 1) && w(x + 1, y + 1)) inner.add(i)
                else if (n in 1..3) shore.add(i)
            } else if (grass[i]) {
                gr.add(i)
                var ok = true
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= cw || ny >= ch || water[ny * cw + nx] || !grass[ny * cw + nx]) ok = false
                }
                if (ok) meadow.add(i)
            }
        }
        innerWater = inner.toIntArray()
        shoreWater = shore.toIntArray()
        grassCells = gr.toIntArray()
        meadowCells = meadow.toIntArray()
    }

    // ---- drop shadows: a black copy of each sprite, flattened and sheared in the draw ----
    val shadowPaint = android.graphics.Paint()
    val nightPaint = android.graphics.Paint()
    val lightPaint = android.graphics.Paint().apply {
        isAntiAlias = true
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT)
    }
    private val shadowCache = java.util.IdentityHashMap<ImageBitmap, Bitmap>()

    fun shadowOf(sp: ImageBitmap): Bitmap = shadowCache.getOrPut(sp) {
        val w = sp.width
        val h = sp.height
        val px = IntArray(w * h)
        sp.asAndroidBitmap().getPixels(px, 0, w, 0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(PixelFx.silhouette(px), 0, w, 0, 0, w, h)
        bmp
    }

    // ---- bits of life that remember a little between frames ----
    val lastGather = HashMap<Int, Int>()
    val floaters = ArrayList<Floater>()

    val oak = sprite(OAK, mapOf(
        'g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2C6B33.toInt(), 'h' to 0xFF6BB85A.toInt(),
        't' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(),
    ))
    val pine = sprite(PINE, mapOf(
        'p' to 0xFF2F7F52.toInt(), 'P' to 0xFF1F5A3B.toInt(), 'q' to 0xFF4E9E6A.toInt(),
        't' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(),
    ))
    val stump = sprite(STUMP, mapOf('t' to 0xFF7A4B24.toInt(), 'T' to 0xFF54331A.toInt(), 'c' to 0xFFD9B77E.toInt()))
    val rock = sprite(ROCK, mapOf('r' to 0xFF9AA0AA.toInt(), 'R' to 0xFFC9CED6.toInt(), 'd' to 0xFF6B707A.toInt()))
    private val bushPal = mapOf('g' to 0xFF3E8E41.toInt(), 'G' to 0xFF2C6B33.toInt(), 'h' to 0xFF6BB85A.toInt(), 'r' to 0xFFE0405A.toInt())
    val bush = sprite(BUSH, bushPal)
    val bushEmpty = sprite(BUSH.map { it.replace('r', 'g') }, bushPal)
    val hut = sprite(HUT, mapOf(
        'r' to 0xFFB5533C.toInt(), 'R' to 0xFF8F3E2C.toInt(), 'S' to 0xFFD97A5C.toInt(),
        'w' to 0xFFDDBB82.toInt(), 'W' to 0xFFBF9C63.toInt(), 'd' to 0xFF4A2E1B.toInt(),
        'k' to 0xFF9FD3F0.toInt(), 'b' to 0xFF8A6A3E.toInt(), 'c' to 0xFF6E6E78.toInt(),
    ))
    val hutStone = sprite(HUT, mapOf(
        'r' to 0xFF6F7A8C.toInt(), 'R' to 0xFF4F5868.toInt(), 'S' to 0xFF8E99AB.toInt(),
        'w' to 0xFFB9B9BF.toInt(), 'W' to 0xFF95959D.toInt(), 'd' to 0xFF3E2A1B.toInt(),
        'k' to 0xFF9FD3F0.toInt(), 'b' to 0xFF6B6B74.toInt(), 'c' to 0xFF6E6E78.toInt(),
    ))
    val townHouse = sprite(TOWNHOUSE, mapOf(
        'r' to 0xFFA04A38.toInt(), 'R' to 0xFF7C3A2B.toInt(), 'c' to 0xFF6E6E78.toInt(),
        'w' to 0xFFE7DCC2.toInt(), 'W' to 0xFFC9B99A.toInt(), 'k' to 0xFF9FD3F0.toInt(),
        'd' to 0xFF4A2E1B.toInt(), 'b' to 0xFF8D909C.toInt(),
    ))
    val cityHouse = sprite(CITYHOUSE, mapOf(
        'r' to 0xFF5B6B8C.toInt(), 'R' to 0xFF3F4C6B.toInt(), 'p' to 0xFF5A3A22.toInt(), 'f' to 0xFFD94F4F.toInt(),
        'w' to 0xFFEDE3CC.toInt(), 'W' to 0xFFCFC2A6.toInt(), 'k' to 0xFF9FD3F0.toInt(),
        'd' to 0xFF4A2E1B.toInt(), 'b' to 0xFF8D909C.toInt(),
    ))
    val hall = sprite(HALL, mapOf(
        'r' to 0xFFC9A04A.toInt(), 'R' to 0xFF9C7A2E.toInt(), 'p' to 0xFF5A3A22.toInt(), 'f' to 0xFF4F9BFF.toInt(),
        'w' to 0xFFA8743A.toInt(), 'W' to 0xFF86582A.toInt(), 'k' to 0xFFF2D88A.toInt(),
        'd' to 0xFF3B2412.toInt(), 'b' to 0xFF7A6A55.toInt(),
    ))
    val tent = sprite(TENT, mapOf(
        'p' to 0xFF6B4A2B.toInt(), 't' to 0xFFC9A26B.toInt(), 'T' to 0xFFA67E4B.toInt(), 'd' to 0xFF3B2A1A.toInt(),
    ))
    val oreRock = sprite(ORE, mapOf(
        'r' to 0xFF9AA0AA.toInt(), 'R' to 0xFFC9CED6.toInt(), 'd' to 0xFF6B707A.toInt(),
        'o' to 0xFFD98A3D.toInt(), 'O' to 0xFFF2D06B.toInt(),
    ))
    val forge = sprite(FORGE, mapOf(
        'c' to 0xFF4A4A52.toInt(), 'C' to 0xFF2E2E34.toInt(), 's' to 0xFF8A8A94.toInt(), 'S' to 0xFF62626C.toInt(),
        'O' to 0xFFFF7A21.toInt(), 'y' to 0xFFFFD84A.toInt(),
    ))
    val wolf = sprite(WOLF, mapOf(
        'w' to 0xFF8E8E98.toInt(), 'W' to 0xFFB8B8C2.toInt(), 'd' to 0xFF50505A.toInt(),
        'e' to 0xFFFF4040.toInt(), 'n' to 0xFF222222.toInt(),
    ))
    val bear = sprite(BEAR, mapOf(
        'b' to 0xFF6B4A2B.toInt(), 'B' to 0xFF4A3220.toInt(), 'e' to 0xFFFF3030.toInt(), 'n' to 0xFFC9A26B.toInt(),
    ))
    val boar = sprite(BOAR, mapOf(
        'b' to 0xFF6B4A33.toInt(), 'B' to 0xFF4A3322.toInt(), 'k' to 0xFF2A1C12.toInt(),
        'w' to 0xFFF2E6C8.toInt(), 'e' to 0xFFFF4040.toInt(), 'p' to 0xFFD99A8A.toInt(),
    ))
    val tower = sprite(TOWER, mapOf(
        's' to 0xFFB9BCC6.toInt(), 'S' to 0xFF8D909C.toInt(), 'k' to 0xFF2A2A34.toInt(),
        'w' to 0xFF8A5A2B.toInt(), 'W' to 0xFF6B4220.toInt(), 'r' to 0xFFB8452F.toInt(), 'R' to 0xFF8E3322.toInt(),
        'f' to 0xFFFFC34A.toInt(), 'd' to 0xFF4A2E1B.toInt(),
    ))
    val palisade = sprite(PALISADE, mapOf(
        'w' to 0xFF9A6B38.toInt(), 'W' to 0xFF7A5028.toInt(), 'k' to 0xFF3A2614.toInt(),
    ))
    private val deerPal = mapOf('d' to 0xFFB98A52.toInt(), 'D' to 0xFFF2E3C4.toInt(), 'a' to 0xFF6B4A2B.toInt(), 'n' to 0xFF2A1C12.toInt())
    val deer = listOf(sprite(DEER1, deerPal), sprite(DEER2, deerPal))
    val farm = sprite(FARM, mapOf(
        'd' to 0xFF6B4A2B.toInt(), 'D' to 0xFF573A20.toInt(), 'g' to 0xFF7BC043.toInt(),
        'G' to 0xFFE8C547.toInt(), 'f' to 0xFF8B6B45.toInt(),
    ))
    val well = sprite(WELL, mapOf(
        'R' to 0xFFB5533C.toInt(), 'b' to 0xFF7A4B24.toInt(), 's' to 0xFF9AA0AA.toInt(), 'S' to 0xFF2B68AE.toInt(), 'd' to 0xFF6B707A.toInt(),
    ))
    private val chickPal = mapOf('w' to 0xFFF4F0E6.toInt(), 'o' to 0xFFE89A2B.toInt(), 'r' to 0xFFD9373A.toInt(), 'd' to 0xFF222222.toInt(), 'g' to 0xFFC9C3B4.toInt())
    val chicken = listOf(sprite(CHICK1, chickPal), sprite(CHICK2, chickPal))
    private val firePal = mapOf('y' to 0xFFFFE070.toInt(), 'o' to 0xFFFF8A2B.toInt(), 'L' to 0xFF6B4A2B.toInt(), 'l' to 0xFF4A3220.toInt(), 's' to 0xFF8E8E96.toInt())
    private val rabbitPal = mapOf('b' to 0xFFB59A7A.toInt(), 'e' to 0xFF9A8060.toInt(), 'w' to 0xFFF4F0E6.toInt(), 'n' to 0xFFD99A8A.toInt(), 'd' to 0xFF222222.toInt())
    val rabbit = listOf(sprite(RABBIT1, rabbitPal), sprite(RABBIT2, rabbitPal))
    private val duckPal = mapOf('g' to 0xFF2F8F58.toInt(), 'o' to 0xFFE89A2B.toInt(), 'w' to 0xFFF4F0E6.toInt(), 'b' to 0xFFB59A7A.toInt(), 'd' to 0xFF222222.toInt())
    val duck = listOf(sprite(DUCK1, duckPal), sprite(DUCK2, duckPal))
    val fire = listOf(sprite(FIRE1, firePal, outline = false, shade = false), sprite(FIRE2, firePal, outline = false, shade = false))

    val cactus = sprite(CACTUS, mapOf('g' to 0xFF4E9E4A.toInt(), 'G' to 0xFF3A7A38.toInt()))
    val elfHouse = sprite(ELF_HOUSE, mapOf(
        'g' to 0xFF4FAE5C.toInt(), 'G' to 0xFF2F7A3E.toInt(), 'h' to 0xFF8ADB7A.toInt(),
        'w' to 0xFFE9DDB8.toInt(), 'k' to 0xFFBFE9FF.toInt(), 'd' to 0xFF5A3A22.toInt(), 'b' to 0xFF8A6A3E.toInt(),
    ))
    val orcHut = sprite(ORC_HUT, mapOf(
        'k' to 0xFFE8E0C8.toInt(), 's' to 0xFF7A6A55.toInt(), 'h' to 0xFF7A5A3A.toInt(),
        'R' to 0xFFC0392B.toInt(), 'd' to 0xFF2A1A10.toInt(), 'b' to 0xFF4A3220.toInt(),
    ))
    val dwarfHall = sprite(DWARF_HALL, mapOf(
        'r' to 0xFF8A4B2B.toInt(), 'R' to 0xFF6B3A20.toInt(), 'c' to 0xFF55555E.toInt(),
        's' to 0xFFA6A6B0.toInt(), 'S' to 0xFF7E7E88.toInt(), 'd' to 0xFF2A1A10.toInt(),
        'O' to 0xFFFFA32B.toInt(), 'b' to 0xFF5E5E68.toInt(),
    ))
    val keep = sprite(KEEP, mapOf(
        's' to 0xFFB9BCC6.toInt(), 'S' to 0xFF8D909C.toInt(), 'k' to 0xFF2A2A34.toInt(),
        'd' to 0xFF4A2E1B.toInt(), 'm' to 0xFF9C9FAB.toInt(),
    ))
    val ship = sprite(SHIP, mapOf(
        'm' to 0xFF5A3A22.toInt(), 'w' to 0xFFF4F0E6.toInt(), 'b' to 0xFF8A5A2B.toInt(), 'B' to 0xFF6B4220.toInt(),
    ))

    fun homeFor(race: String): ImageBitmap = when (race) {
        "Elf" -> elfHouse
        "Orc" -> orcHut
        else -> dwarfHall
    }

    private fun folkSprites(skin: Int, skinShade: Int, hair: Int, shirt: Int, shade: Int, top: List<String>?, beard: Boolean, tusks: Boolean): List<ImageBitmap> {
        val pal = mapOf(
            'r' to hair, 'h' to skin, 'H' to skinShade, 'd' to 0xFF222222.toInt(),
            's' to shirt, 'S' to shade, 'p' to 0xFF3A3F5C.toInt(), 'b' to 0xFF3B2A1A.toInt(), 'w' to 0xFFF4F0E6.toInt(),
        )
        fun tweak(g: List<String>): List<String> {
            val rows = g.toMutableList()
            if (top != null) rows[0] = top[0]
            if (beard) {
                rows[4] = ".rrrrrr."
                rows[5] = ".srrrrs."
            }
            if (tusks) rows[4] = ".whHHhw."
            return rows
        }
        return listOf(sprite(tweak(PERSON1), pal), sprite(tweak(PERSON2), pal))
    }

    private val folkMap = mapOf(
        "Elf" to folkSprites(0xFFF0D8B0.toInt(), 0xFFD9BC90.toInt(), 0xFFE8E0A0.toInt(), 0xFFC77DFF.toInt(), 0xFF9A55D1.toInt(), listOf("h.rrrr.h"), false, false),
        "Orc" to folkSprites(0xFF7DB84A.toInt(), 0xFF5E9234.toInt(), 0xFF222222.toInt(), 0xFFE8503A.toInt(), 0xFFB03A28.toInt(), null, false, true),
        "Dwarf" to folkSprites(0xFFF2C7A0.toInt(), 0xFFD9A982.toInt(), 0xFFB5651D.toInt(), 0xFFF0B13C.toInt(), 0xFFB98A27.toInt(), null, true, false),
    )

    fun folk(race: String, frame: Int): ImageBitmap = (folkMap[race] ?: folkMap.getValue("Elf"))[frame and 1]

    private val shirts = intArrayOf(0xFFD94F4F.toInt(), 0xFF4F7FD9.toInt(), 0xFFE0B040.toInt(), 0xFF8E4FD9.toInt(), 0xFF4FB58E.toInt(), 0xFFE07A3A.toInt(), 0xFFD94F9B.toInt(), 0xFFEFEFEF.toInt(), 0xFF8B5A2B.toInt(), 0xFFCD7F32.toInt(), 0xFF4A4F5A.toInt())
    private val shirtShade = intArrayOf(0xFFA83A3A.toInt(), 0xFF3A62A8.toInt(), 0xFFB48C2E.toInt(), 0xFF6D3AA8.toInt(), 0xFF3A8D6E.toInt(), 0xFFB05E2A.toInt(), 0xFFA83A78.toInt(), 0xFFC0C0C0.toInt(), 0xFF6B4220.toInt(), 0xFF9A5F22.toInt(), 0xFF2F333B.toInt())
    private val hairs = intArrayOf(0xFF3B2A1A.toInt(), 0xFF8A5A2B.toInt(), 0xFF222222.toInt(), 0xFFD9B04A.toInt(), 0xFF7A2E1E.toInt(), 0xFFB0B0B0.toInt(), 0xFF5A3A6A.toInt(), 0xFF2E4A3A.toInt(), 0xFF3B2A1A.toInt(), 0xFF7A2E1E.toInt(), 0xFF222222.toInt())
    private val people = List(10) { i ->
        val pal = mapOf(
            'r' to hairs[i], 'h' to 0xFFF2CBA3.toInt(), 'H' to 0xFFD9A982.toInt(), 'd' to 0xFF222222.toInt(),
            's' to shirts[i], 'S' to shirtShade[i], 'p' to 0xFF3A3F5C.toInt(), 'b' to 0xFF3B2A1A.toInt(),
        )
        listOf(sprite(PERSON1, pal), sprite(PERSON2, pal))
    }

    fun person(colorIdx: Int, frame: Int): ImageBitmap = people[colorIdx.coerceIn(0, 9)][frame and 1]

    companion object {
        val OAK = listOf(
            "....hhhhhh....",
            "..hhhhgggggg..",
            ".hhhggggggGgg.",
            ".hhgggggggggg.",
            "hhgggGgggggggG",
            "hgggggggGggggg",
            "gggGgggggggGgg",
            ".ggggggGggggg.",
            ".GgggggggggGg.",
            "..GGgggggGGG..",
            "....GGGGGG....",
            "......tt......",
            "......tT......",
            "......tT......",
            ".....tttT.....",
            "....ttTTTt....",
        )
        val PINE = listOf(
            ".....qq.....",
            "....qppp....",
            "....ppPp....",
            "...qpppPp...",
            "...pppPpp...",
            "..qppppPpp..",
            "..pppPpppp..",
            ".qpppppPppp.",
            ".ppPpppppPp.",
            "qppppPpppppp",
            "pppPpppppPpp",
            "....tttt....",
            "....tTtt....",
            "....tTtt....",
        )
        val STUMP = listOf(
            "..cccc..",
            ".cttttc.",
            ".tttTtt.",
            ".tTttTt.",
            "..tTTt..",
        )
        val ROCK = listOf(
            "....rrrr....",
            "..rrRRRrrr..",
            ".rrRRRrrrrrd",
            "rrrRRrrrrrrd",
            "rrrrrrrrdrrd",
            "rrrdrrrrrrdd",
            ".rrrrrrrrddd",
            "..dddddddd..",
        )
        val BUSH = listOf(
            "...hhgg...",
            "..hhgGgg..",
            ".hgggrggGg",
            "hgrgggggGg",
            "ggggGgrggg",
            ".gggggggGg",
            "..GGGGGG..",
        )
        val CACTUS = listOf(
            "...gg...",
            ".g.gg...",
            ".g.gg.g.",
            ".gggg.g.",
            "...ggggg",
            "...gG...",
            "...gG...",
            "...gG...",
        )
        val ELF_HOUSE = listOf(
            "....gggg....",
            "..ggghhggg..",
            ".gghgggggGg.",
            "gggggggggGGg",
            ".ggggggggGG.",
            "..wwwwwwww..",
            "..wkwddwkw..",
            "..wwwddwww..",
            "..wwwddwww..",
            "..bbbbbbbb..",
        )
        val ORC_HUT = listOf(
            "....k..k....",
            "....s..s....",
            ".....ss.....",
            "....hhhh....",
            "...hhhRhh...",
            "..hhhhRhhh..",
            ".hhhhhddhhh.",
            "hhhhhhddhhhh",
            "bbbbbbddbbbb",
        )
        val DWARF_HALL = listOf(
            "......cc....",
            "......cc....",
            "...rrrrrrr..",
            "..rrrrrrrrr.",
            ".rrrrrrrrrrR",
            ".ssssssssss.",
            ".sSssddddsS.",
            ".ssssdOOdss.",
            ".sSsssddssS.",
            "bbbbbbbbbbbb",
        )
        val KEEP = listOf(
            "..m.m.mm.m.m..",
            "..mssssssssm..",
            "..sSsssssssS..",
            "..ssskssksss..",
            "..sSsssssssS..",
            "..ssssssssss..",
            "..ssskssksss..",
            "..sSsssssssS..",
            ".ssssssssssss.",
            ".sSssdddssSss.",
            ".sssddddddsss.",
            ".sSsddddddSss.",
            "SSSSSSSSSSSSSS",
        )
        val SHIP = listOf(
            "......m.......",
            "......mww.....",
            "......mwwww...",
            "......mwwwww..",
            "......m.......",
            "bbbbbbbbbbbbb.",
            ".bBBBBBBBBBb..",
            "..bbbbbbbbb...",
        )
        val HUT = listOf(
            "......RR......",
            "....SSrrRR....",
            "..SSrrrrrrRR..",
            ".SrrrrrrrrrrR.",
            "rrrrrrrrrrrrrR",
            ".wwwwwwwwwwww.",
            ".wkkwwwwwkkw..".replace("..", "W."),
            ".wkkwddddwkkw.",
            ".wwwwddddwwww.",
            ".WwwwddddwwwW.",
            ".bbbbbbbbbbbb.",
        )
        val TOWNHOUSE = listOf(
            "....cc........",
            "....cc..rrrr..",
            "...rrrrrrRRRR.",
            "..rrrrrrrRRRRR",
            ".rrrrrrrrrRRRR",
            "rrrrrrrrrrRRRR",
            ".wwwwwwwwwwww.",
            ".wkkwWwwwWkkw.",
            ".wkkwwwwwwkkw.",
            ".wwwwwddwwwww.",
            ".WwwwwddwwwwW.",
            ".bbbbbddbbbbb.",
        )
        val CITYHOUSE = listOf(
            "......p.......",
            "......pff.....",
            "....rrrrrr....",
            "...rrrrrrRR...",
            "..rrrrrrrRRR..",
            ".rrrrrrrrrRRR.",
            ".wwwwwwwwwwww.",
            ".wkkwwwwwwkkw.",
            ".wkkwwwwwwkkw.",
            ".wwwwwwwwwwww.",
            ".wkkwwddwwkkw.",
            ".WwwwwddwwwwW.",
            ".bbbbbddbbbbb.",
        )
        val HALL = listOf(
            "......p.........",
            "......pff.......",
            "....rrrrrrrr....",
            "..rrrrrrrrrrRR..",
            ".rrrrrrrrrrrrRR.",
            "rrrrrrrrrrrrrrRR",
            ".wwwwwwwwwwwwww.",
            ".wwkkwwddwwkkww.",
            ".WwkkwwddwwkkwW.",
            ".bbbbbbddbbbbbb.",
        )
        val TENT = listOf(
            "......pp......",
            ".....tttT.....",
            "....ttttTT....",
            "...tttttTTT...",
            "..tttttddTTT..",
            ".tttttdddTTTT.",
            "tttttttdddTTTT",
            "ttttttddddTTTT",
        )
        val ORE = listOf(
            "....rrrr....",
            "..rrRRRrrr..",
            ".rrRoRrrorrd",
            "rrrRRrrrOrrd",
            "rorrrrrrdrrd",
            "rrrdrorrrrdd",
            ".rrrrrrOrddd",
            "..dddddddd..",
        )
        val FORGE = listOf(
            "....cccc....",
            "....cCCc....",
            "....cCCc....",
            "...cccccc...",
            "..ssssssss..",
            ".ssssssssss.",
            ".ssOOOOOOss.",
            ".ssOyyyyOss.",
            ".ssOyyyyOss.",
            ".ssssssssss.",
            "SSSSSSSSSSSS",
        )
        val WOLF = listOf(
            "......d.d.",
            ".....wwwww",
            "ww..wwwWen",
            "wwwwwwwww.",
            "wwwwwwwww.",
            ".d.d..d.d.",
            ".d.d..d.d.",
        )
        val BEAR = listOf(
            "........b.b.",
            "..bbbbbbbbbb",
            ".bbbbbbbbebn",
            "bbbbbbbbbbbb",
            "bbbBbbbbbBbb",
            "bbbbbbbbbbbb",
            ".bbbbbbbbbb.",
            ".BB.BB..BB..",
            ".BB.BB..BB..",
        )
        val BOAR = listOf(
            "..........",
            "...kk.....",
            "bbbbbbbb..",
            "bBbbbbbbbb",
            "bbbbbbbbew",
            "bbbbbbbbpw",
            ".bbbbbbbbp",
            ".B.B..B.B.",
            ".B.B..B.B.",
        )
        val PALISADE = listOf(
            "w.w.w.w.w.w.",
            "wWwWwWwWwWwW",
            "wWwWwWwWwWwW",
            "wWwWwWwWwWwW",
            "kkkkkkkkkkkk",
        )
        val DEER1 = listOf(
            "......a.a...",
            ".......aa...",
            "......dddn..",
            ".dddddddd...",
            "dddddddddd..",
            "dddddddddd..",
            ".dDdDdDdDd..",
            ".d.d..d.d...",
            ".d.d..d.d...",
        )
        val DEER2 = listOf(
            "......a.a...",
            ".......aa...",
            "......dddn..",
            ".dddddddd...",
            "dddddddddd..",
            "dddddddddd..",
            ".dDdDdDdDd..",
            "..d.d.d.d...",
            ".d..d..d.d..",
        )
        val TOWER = listOf(
            "....rrrr....",
            "...rrRRrr...",
            "..rrRRRRrr..",
            "...wwwwww...",
            "..wWwwwwWw..",
            "..wwkkkkww..",
            "...ssssss...",
            "...sSssSs...",
            "...skkkks...",
            "...ssssss...",
            "...sSssSs...",
            "...ssssss...",
            "..ssSssSss..",
            "..sssddsss..",
            ".SSSSddSSSS.",
        )
        val FARM = listOf(
            "ffffffffffffff",
            "dDdDdDdDdDdDdD",
            "dddddddddddddd",
            "dDdDdDdDdDdDdD",
            "dddddddddddddd",
            "dDdDdDdDdDdDdD",
            "dddddddddddddd",
            "dDdDdDdDdDdDdD",
            "dddddddddddddd",
            "ffffffffffffff",
        )
        val WELL = listOf(
            "..RRRR..",
            ".RRRRRR.",
            "..b..b..",
            "..b..b..",
            ".ssssss.",
            "ssSSSSss",
            "sdssssds",
            ".dddddd.",
        )
        val CHICK1 = listOf(
            "...r...",
            "..wwd..",
            ".wwwwo.",
            "wwwwg..",
            ".wwwg..",
            "..o.o..",
        )
        val CHICK2 = listOf(
            ".......",
            ".......",
            "..wwwd.",
            ".wwwwwo",
            "wwwwgr.",
            "..o.o..",
        )
        val FIRE1 = listOf(
            "....y...",
            "...yoy..",
            "..yooyy.",
            "..yooy..",
            "..LlLlL.",
            ".LLLLLL.",
            "..ssss..",
        )
        val FIRE2 = listOf(
            "...y....",
            "..yoy...",
            "..yooy..",
            ".yyooyy.",
            "..LlLlL.",
            ".LLLLLL.",
            "..ssss..",
        )
        val RABBIT1 = listOf(
            "......e.",
            "......ee",
            ".....bbb",
            ".bbbbbbn",
            "wbbbbbb.",
            ".bb..bb.",
        )
        val RABBIT2 = listOf(
            "......e.",
            ".....ee.",
            ".....bbb",
            "bbbbbbbn",
            "wbbbbbb.",
            "bb....bb",
        )
        val DUCK1 = listOf(
            ".....gg.",
            "....gdgo",
            ".bbbwwg.",
            "bbbbwww.",
            ".wwwww..",
        )
        val DUCK2 = listOf(
            ".....gg.",
            "....gdgo",
            ".bbbwwg.",
            ".bbbwww.",
            "..wwww..",
        )
        val PERSON1 = listOf(
            "..rrrr..",
            ".rrrrrr.",
            ".rhhhhr.",
            ".hdhhdh.",
            "..hHHh..",
            ".ssssss.",
            "hsSssSsh",
            "hssssssh",
            ".sSssSs.",
            ".pppppp.",
            ".pp..pp.",
            ".bb..bb.",
        )
        val PERSON2 = listOf(
            "..rrrr..",
            ".rrrrrr.",
            ".rhhhhr.",
            ".hdhhdh.",
            "..hHHh..",
            ".ssssss.",
            "hsSssSsh",
            "hssssssh",
            ".sSssSs.",
            ".pppppp.",
            "..pppp..",
            "..bbbb..",
        )
    }
}
