package xyz.nim.civutils.gui.theme

import xyz.nim.civutils.gui.layout.SimpleLayout

/**
 * Responsive layout calculations based on screen size.
 * Provides helper methods for common layout patterns.
 */
class ResponsiveLayout(val screenWidth: Int, val screenHeight: Int) {

    val size: CivutilsTheme.ScreenSize = CivutilsTheme.getScreenSize(screenWidth, screenHeight)
    val margin: Int = CivutilsTheme.margin(size)
    val padding: Int = CivutilsTheme.padding(size)
    val spacing: Int = CivutilsTheme.spacing(size)
    val buttonHeight: Int = CivutilsTheme.buttonHeight(size)
    val headerHeight: Int = CivutilsTheme.headerHeight(size)
    val controlHeight: Int = CivutilsTheme.controlHeight(size)
    val buttonWidth: Int = CivutilsTheme.buttonWidth(size)
    val smallButtonWidth: Int = CivutilsTheme.smallButtonWidth(size)

    // Content area (inside margins)
    fun contentX(): Int = margin
    fun contentY(): Int = margin
    fun contentWidth(): Int = screenWidth - margin * 2
    fun contentHeight(): Int = screenHeight - margin * 2

    // Center helpers
    fun centerX(elementWidth: Int): Int = (screenWidth - elementWidth) / 2
    fun centerY(elementHeight: Int): Int = (screenHeight - elementHeight) / 2

    /**
     * Create a two-panel split layout.
     * @param leftRatio The ratio of content width for the left panel (0.0 to 1.0)
     */
    fun split(leftRatio: Float): SplitLayout {
        val totalWidth = contentWidth()
        val leftWidth = (totalWidth * leftRatio).toInt()
        val rightWidth = totalWidth - leftWidth - spacing
        return SplitLayout(contentX(), contentY(), leftWidth, rightWidth, contentHeight(), spacing)
    }

    /**
     * Create a centered panel with maximum width.
     */
    fun centered(maxWidth: Int): SimpleLayout.Box {
        val w = minOf(maxWidth, contentWidth())
        val x = (screenWidth - w) / 2
        return SimpleLayout.Box(x, contentY(), w, contentHeight())
    }

    /**
     * Create a centered panel with maximum width and height.
     */
    fun centered(maxWidth: Int, maxHeight: Int): SimpleLayout.Box {
        val w = minOf(maxWidth, contentWidth())
        val h = minOf(maxHeight, contentHeight())
        val x = (screenWidth - w) / 2
        val y = (screenHeight - h) / 2
        return SimpleLayout.Box(x, y, w, h)
    }

    /**
     * Get the full content area as a Box.
     */
    fun fullContent(): SimpleLayout.Box {
        return SimpleLayout.Box(contentX(), contentY(), contentWidth(), contentHeight())
    }

    /**
     * Create a VStack in the content area.
     */
    fun vstack(): SimpleLayout.VStack {
        return SimpleLayout.VStack(contentX(), contentY(), contentWidth(), spacing)
    }

    /**
     * Create an HStack in the content area.
     */
    fun hstack(): SimpleLayout.HStack {
        return SimpleLayout.HStack(contentX(), contentY(), contentHeight(), spacing)
    }

    /**
     * Two-panel split layout helper.
     */
    data class SplitLayout(
        val x: Int,
        val y: Int,
        val leftWidth: Int,
        val rightWidth: Int,
        val height: Int,
        val gap: Int
    ) {
        fun left(): SimpleLayout.Box = SimpleLayout.Box(x, y, leftWidth, height)
        fun right(): SimpleLayout.Box = SimpleLayout.Box(x + leftWidth + gap, y, rightWidth, height)
        fun rightX(): Int = x + leftWidth + gap
    }
}
