package xyz.nim.civutils.data.playertag

/**
 * A snapshot of a player's location at a point in time.
 */
data class LocationSnapshot(
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: String = "minecraft:overworld",
    val serverAddress: String = ""
)
