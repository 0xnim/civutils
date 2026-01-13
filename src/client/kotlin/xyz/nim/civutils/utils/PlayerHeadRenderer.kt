package xyz.nim.civutils.utils

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.PlayerFaceRenderer
import net.minecraft.client.multiplayer.PlayerInfo
import net.minecraft.resources.ResourceLocation
import java.util.UUID

/**
 * Utility for rendering player heads in GUI screens.
 * Uses Minecraft's built-in PlayerFaceRenderer for accurate face rendering.
 */
object PlayerHeadRenderer {

    private val mc: Minecraft get() = Minecraft.getInstance()

    // Default Steve skin for fallback
    private val STEVE_SKIN = ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png")

    /**
     * Render a player's head at the given position.
     * Uses the player's actual skin if available, otherwise falls back to Steve.
     *
     * @param guiGraphics The graphics context
     * @param playerName The player's name (for online player lookup)
     * @param uuid Optional UUID (for skin lookup if player is offline)
     * @param x X position
     * @param y Y position
     * @param size Size of the head (width and height)
     */
    fun renderHead(
        guiGraphics: GuiGraphics,
        playerName: String,
        uuid: String? = null,
        x: Int,
        y: Int,
        size: Int = 16
    ) {
        val playerInfo = getPlayerInfo(playerName)
        if (playerInfo != null) {
            // Use Minecraft's built-in PlayerFaceRenderer for accurate face rendering
            PlayerFaceRenderer.draw(guiGraphics, playerInfo.skin, x, y, size)
        } else {
            // Fallback: render a placeholder with player initial
            renderPlaceholder(guiGraphics, playerName, x, y, size)
        }
    }

    /**
     * Render a player head for an online player (from PlayerInfo).
     */
    fun renderHead(
        guiGraphics: GuiGraphics,
        playerInfo: PlayerInfo,
        x: Int,
        y: Int,
        size: Int = 16
    ) {
        PlayerFaceRenderer.draw(guiGraphics, playerInfo.skin, x, y, size)
    }

    /**
     * Get PlayerInfo for a player by name.
     */
    private fun getPlayerInfo(playerName: String): PlayerInfo? {
        return mc.connection?.listedOnlinePlayers?.find {
            it.profile.name.equals(playerName, ignoreCase = true)
        }
    }

    /**
     * Render a placeholder when skin is not available.
     * Shows a colored box with the player's initial.
     */
    private fun renderPlaceholder(
        guiGraphics: GuiGraphics,
        playerName: String,
        x: Int,
        y: Int,
        size: Int
    ) {
        // Generate a consistent color from the player name
        val color = getColorFromName(playerName)
        val colorWithAlpha = color or (0xFF shl 24)

        // Draw background
        guiGraphics.fill(x, y, x + size, y + size, colorWithAlpha)

        // Draw initial
        val font = mc.font
        val initial = playerName.firstOrNull()?.uppercase() ?: "?"
        val textX = x + (size - font.width(initial)) / 2
        val textY = y + (size - 8) / 2
        guiGraphics.drawString(font, initial, textX, textY, 0xFFFFFF, true)
    }

    /**
     * Generate a consistent color from a player name.
     */
    private fun getColorFromName(name: String): Int {
        val hash = name.hashCode()
        // Use the hash to generate RGB values in a pleasing range
        val r = ((hash and 0xFF0000) shr 16).coerceIn(60, 200)
        val g = ((hash and 0x00FF00) shr 8).coerceIn(60, 200)
        val b = (hash and 0x0000FF).coerceIn(60, 200)
        return (r shl 16) or (g shl 8) or b
    }

    /**
     * Render a bordered player head with a colored border indicating tag status.
     *
     * @param guiGraphics The graphics context
     * @param playerName The player's name
     * @param uuid Optional UUID
     * @param x X position
     * @param y Y position
     * @param size Size of the head
     * @param borderColor Color of the border (ARGB), or null for no border
     */
    fun renderHeadWithBorder(
        guiGraphics: GuiGraphics,
        playerName: String,
        uuid: String? = null,
        x: Int,
        y: Int,
        size: Int = 16,
        borderColor: Int? = null
    ) {
        if (borderColor != null) {
            // Draw border (2 pixels thick for visibility)
            val borderWithAlpha = borderColor or (0xFF shl 24)
            guiGraphics.fill(x - 2, y - 2, x + size + 2, y, borderWithAlpha)           // top
            guiGraphics.fill(x - 2, y + size, x + size + 2, y + size + 2, borderWithAlpha) // bottom
            guiGraphics.fill(x - 2, y, x, y + size, borderWithAlpha)                   // left
            guiGraphics.fill(x + size, y, x + size + 2, y + size, borderWithAlpha)     // right
        }

        renderHead(guiGraphics, playerName, uuid, x, y, size)
    }
}
