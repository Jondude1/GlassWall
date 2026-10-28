package com.jondude1.glasswall.sim

/** One kingdom on the map. Id 0 is the player's own village, the rest are neighbours with their own banners. */
class Kingdom(val id: Int, var name: String, val race: String, val color: Long, val cx: Int, val cy: Int) {
    var pop = 6
    var might = 10f
    var fallen = false
    var fallenAt = 0.0
    var radius = 4f
    val homes = ArrayList<IntArray>()
}

/** How two kingdoms stand: war, alliance or trade. Peace is the absence of one. */
class Rel(val a: Int, val b: Int, var kind: String, var until: Double)

/** A short visual effect from a god power or a battle. */
class Fx(val type: String, val x: Float, val y: Float, val born: Double, val life: Float)

/** A multi-day age of the whole world, in the WorldBox spirit. mood is the change per twelve seconds. */
class WorldAgeInfo(
    val name: String,
    val blurb: String,
    val tint: Long,
    val tintA: Float,
    val particle: String,
    val mood: Float,
    val farm: Float,
    val war: Float,
    val darkFloor: Float,
)

/** The settlement's ages, one per stage of growth. index = Civ.age. */
val WORLD_AGES = listOf(
    WorldAgeInfo("Camp Age", "Tents by the fire. Hunt, forage, survive.", 0xFFFFE9B0, 0.04f, "motes", 0f, 1.0f, 1f, 0f),
    WorldAgeInfo("Hut Age", "Timber huts and the first farms.", 0xFFFFF0B0, 0.03f, "motes", 0f, 1.0f, 1f, 0f),
    WorldAgeInfo("Village Age", "Stone houses, a village hall and forges.", 0xFFFFE0A0, 0.02f, "none", 0f, 1.1f, 1f, 0f),
    WorldAgeInfo("Town Age", "Streets, towers and iron tools.", 0xFFFFD8A0, 0.02f, "none", 0f, 1.2f, 1f, 0f),
    WorldAgeInfo("City Age", "A walled city with a grand keep.", 0xFFFFD0A0, 0.03f, "none", 0f, 1.3f, 1f, 0f),
)

/** Meat and hides a beast left where it fell, waiting to be hauled home. */
class Drop(val kind: String, val x: Float, val y: Float, val born: Double, val food: Int, val hides: Int) {
    var by = -1
}

fun predatorMaxHp(kind: String): Float = when (kind) { "bear" -> 90f; "boar" -> 60f; else -> 40f }

/** A wolf, boar or bear hunting near the village. */
class Predator(val kind: String, var x: Float, var y: Float) {
    var hp = predatorMaxHp(kind)
    var cool = 0f
    var life = 0f
    var angle = 0f
}


val RACE_NAMES = mapOf(
    "Elf" to listOf("Sylvaran Court", "Aelthorn", "Lorien Glade", "Moonwhisper"),
    "Orc" to listOf("Grukhar Horde", "Blackmaw Clan", "Skullgate Warband", "Redtusk"),
    "Dwarf" to listOf("Ironhearth", "Deepforge Hold", "Stonebeard Clan", "Anvilgate"),
)
