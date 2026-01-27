package xyz.nim.civutils.utils

import net.minecraft.client.gui.GuiGraphics

/**
 * GUI utility extension functions.
 *
 * Contains compatibility helpers for APIs that changed between Minecraft versions.
 */

/**
 * Draws an outline around a rectangle.
 *
 * This is a compatibility replacement for GuiGraphics.renderOutline which was
 * removed in Minecraft 1.21.9.
 *
 * @param x The x coordinate of the top-left corner
 * @param y The y coordinate of the top-left corner
 * @param width The width of the rectangle
 * @param height The height of the rectangle
 * @param color The color of the outline (ARGB format)
 */
fun GuiGraphics.renderOutline(x: Int, y: Int, width: Int, height: Int, color: Int) {
    // Top edge
    fill(x, y, x + width, y + 1, color)
    // Bottom edge
    fill(x, y + height - 1, x + width, y + height, color)
    // Left edge
    fill(x, y + 1, x + 1, y + height - 1, color)
    // Right edge
    fill(x + width - 1, y + 1, x + width, y + height - 1, color)
}
