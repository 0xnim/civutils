package xyz.nim.civutils.overlays

import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.config.Persisted
import xyz.nim.civutils.core.overlay.OverlayPosition
import xyz.nim.civutils.core.overlay.OverlaySize
import xyz.nim.civutils.core.overlay.TextOverlay
import xyz.nim.civutils.models.PlayerModel

/**
 * Display mode for coordinates.
 */
enum class CoordinateDisplayMode {
    /** Show X, Y, Z on separate lines */
    FULL,
    /** Show X, Z on one line */
    COMPACT,
    /** Show X, Y, Z and facing direction */
    DETAILED
}

/**
 * Example overlay: Shows the player's coordinates.
 *
 * Demonstrates:
 * - Extending TextOverlay for text-based HUD
 * - Using @Persisted for overlay-specific configs
 * - Using a Model for data
 * - Template-based rendering with formatting codes
 * - Preview template for config GUI
 */
class CoordinatesOverlay : TextOverlay(
    position = OverlayPosition.topLeft(offsetX = 5, offsetY = 5),
    size = OverlaySize(width = 150, height = 40)
) {
    override val displayName = "Coordinates"

    /**
     * How to display the coordinates.
     */
    @Persisted
    val displayMode = Config(defaultValue = CoordinateDisplayMode.FULL)

    /**
     * Whether to show the nether coordinates (overworld / 8).
     */
    @Persisted
    val showNetherCoords = Config(defaultValue = false)

    /**
     * Whether to show the biome name.
     */
    @Persisted
    val showBiome = Config(defaultValue = false)

    override fun getTemplate(): String {
        return when (displayMode.value) {
            CoordinateDisplayMode.FULL -> buildFullTemplate()
            CoordinateDisplayMode.COMPACT -> buildCompactTemplate()
            CoordinateDisplayMode.DETAILED -> buildDetailedTemplate()
        }
    }

    override fun getPreviewTemplate(): String {
        return when (displayMode.value) {
            CoordinateDisplayMode.FULL -> """
                §7X: §f123
                §7Y: §f64
                §7Z: §f-456
            """.trimIndent()
            CoordinateDisplayMode.COMPACT -> "§f123§7, §f-456"
            CoordinateDisplayMode.DETAILED -> """
                §7X: §f123 §7Y: §f64 §7Z: §f-456
                §7Facing: §fNorth
            """.trimIndent()
        }
    }

    private fun buildFullTemplate(): String {
        val lines = mutableListOf<String>()

        lines.add("§7X: §f${PlayerModel.blockX}")
        lines.add("§7Y: §f${PlayerModel.blockY}")
        lines.add("§7Z: §f${PlayerModel.blockZ}")

        if (showNetherCoords.value) {
            val netherX = PlayerModel.blockX / 8
            val netherZ = PlayerModel.blockZ / 8
            lines.add("§4Nether: §c$netherX§7, §c$netherZ")
        }

        return lines.joinToString("\n")
    }

    private fun buildCompactTemplate(): String {
        val base = "§f${PlayerModel.blockX}§7, §f${PlayerModel.blockZ}"

        return if (showNetherCoords.value) {
            val netherX = PlayerModel.blockX / 8
            val netherZ = PlayerModel.blockZ / 8
            "$base §7(§c$netherX§7, §c$netherZ§7)"
        } else {
            base
        }
    }

    private fun buildDetailedTemplate(): String {
        val lines = mutableListOf<String>()

        lines.add("§7X: §f${PlayerModel.blockX} §7Y: §f${PlayerModel.blockY} §7Z: §f${PlayerModel.blockZ}")
        lines.add("§7Facing: §f${PlayerModel.facingDirection}")

        if (showNetherCoords.value) {
            val netherX = PlayerModel.blockX / 8
            val netherZ = PlayerModel.blockZ / 8
            lines.add("§4Nether: §c$netherX§7, §c$netherZ")
        }

        return lines.joinToString("\n")
    }
}
