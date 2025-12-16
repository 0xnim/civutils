package xyz.nim.civutils.gui.widgets

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import xyz.nim.civutils.gui.layout.SimpleLayout
import xyz.nim.civutils.gui.theme.CivutilsTheme

/**
 * A panel component with optional header and themed styling.
 */
class CivutilsPanel(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    private val padding: Int
) {
    private var title: String? = null
    private var headerHeight: Int = 24

    /**
     * Set a title for the panel header.
     */
    fun withTitle(title: String): CivutilsPanel {
        this.title = title
        return this
    }

    /**
     * Set a title with custom header height.
     */
    fun withTitle(title: String, headerHeight: Int): CivutilsPanel {
        this.title = title
        this.headerHeight = headerHeight
        return this
    }

    /**
     * Render the panel.
     */
    fun render(context: DrawContext, textRenderer: TextRenderer) {
        // Draw background
        context.fill(x, y, x + width, y + height, CivutilsTheme.PANEL_BG)
        drawBorder(context, x, y, width, height, CivutilsTheme.PANEL_BORDER)

        // Draw header if present
        if (title != null) {
            context.fill(x + 1, y + 1, x + width - 1, y + headerHeight, CivutilsTheme.HEADER_BG)
            context.drawText(textRenderer, title, x + padding, y + (headerHeight - 8) / 2, CivutilsTheme.TEXT_PRIMARY, false)
        }
    }

    private fun drawBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        context.drawHorizontalLine(x, x + w - 1, y, color)
        context.drawHorizontalLine(x, x + w - 1, y + h - 1, color)
        context.drawVerticalLine(x, y, y + h - 1, color)
        context.drawVerticalLine(x + w - 1, y, y + h - 1, color)
    }

    // Content area accessors
    fun contentX(): Int = x + padding
    fun contentY(): Int = y + padding + if (title != null) headerHeight else 0
    fun contentWidth(): Int = width - padding * 2
    fun contentHeight(): Int = height - padding * 2 - if (title != null) headerHeight else 0

    /**
     * Create a VStack in the content area.
     */
    fun vstack(spacing: Int): SimpleLayout.VStack =
        SimpleLayout.VStack(contentX(), contentY(), contentWidth(), spacing)

    /**
     * Create an HStack in the content area.
     */
    fun hstack(spacing: Int): SimpleLayout.HStack =
        SimpleLayout.HStack(contentX(), contentY(), contentHeight(), spacing)

    /**
     * Get the content area as a Box.
     */
    fun contentBox(): SimpleLayout.Box =
        SimpleLayout.Box(contentX(), contentY(), contentWidth(), contentHeight())

    companion object {
        /**
         * Create a panel from coordinates.
         */
        fun create(x: Int, y: Int, width: Int, height: Int, padding: Int): CivutilsPanel =
            CivutilsPanel(x, y, width, height, padding)

        /**
         * Create a panel from a Box.
         */
        fun create(box: SimpleLayout.Box, padding: Int): CivutilsPanel =
            CivutilsPanel(box.x, box.y, box.width, box.height, padding)
    }
}
