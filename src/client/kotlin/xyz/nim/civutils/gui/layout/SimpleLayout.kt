package xyz.nim.civutils.gui.layout

/**
 * Simple layout primitives for building UIs.
 * Provides Box, VStack, HStack, Grid, and Panel structures.
 */
object SimpleLayout {

    /**
     * A rectangular area with position and size.
     */
    data class Box(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int
    ) {
        fun right(): Int = x + width
        fun bottom(): Int = y + height
        fun centerX(): Int = x + width / 2
        fun centerY(): Int = y + height / 2

        /**
         * Check if a point is inside this box.
         */
        fun contains(px: Int, py: Int): Boolean =
            px >= x && px < x + width && py >= y && py < y + height

        /**
         * Create a new box with padding applied inward.
         */
        fun inset(padding: Int): Box =
            Box(x + padding, y + padding, width - padding * 2, height - padding * 2)

        /**
         * Create a new box with different padding on each side.
         */
        fun inset(top: Int, right: Int, bottom: Int, left: Int): Box =
            Box(x + left, y + top, width - left - right, height - top - bottom)
    }

    /**
     * Vertical stack layout - items are placed top to bottom.
     */
    class VStack(
        private val x: Int,
        private val y: Int,
        val width: Int,
        private val spacing: Int
    ) {
        private var currentY: Int = y
        private val items = mutableListOf<Box>()

        /**
         * Add an item with the specified height (uses full width).
         */
        fun add(height: Int): Box {
            val box = Box(x, currentY, width, height)
            items.add(box)
            currentY += height + spacing
            return box
        }

        /**
         * Add an item with custom width and height.
         */
        fun add(itemWidth: Int, height: Int): Box {
            val box = Box(x, currentY, itemWidth, height)
            items.add(box)
            currentY += height + spacing
            return box
        }

        /**
         * Add extra vertical gap.
         */
        fun addGap(gap: Int): VStack {
            currentY += gap
            return this
        }

        /**
         * Get total height of all items added.
         */
        fun getHeight(): Int {
            if (items.isEmpty()) return 0
            return items.last().bottom() - y
        }

        /**
         * Get current Y position (for placing items manually).
         */
        fun getCurrentY(): Int = currentY - spacing

        /**
         * Get the next Y position where an item would be placed.
         */
        fun getNextY(): Int = currentY
    }

    /**
     * Horizontal stack layout - items are placed left to right.
     */
    class HStack(
        private val x: Int,
        private val y: Int,
        val height: Int,
        private val spacing: Int
    ) {
        private var currentX: Int = x
        private val items = mutableListOf<Box>()

        /**
         * Add an item with the specified width (uses full height).
         */
        fun add(width: Int): Box {
            val box = Box(currentX, y, width, height)
            items.add(box)
            currentX += width + spacing
            return box
        }

        /**
         * Add an item with custom width and height.
         */
        fun add(width: Int, itemHeight: Int): Box {
            val box = Box(currentX, y, width, itemHeight)
            items.add(box)
            currentX += width + spacing
            return box
        }

        /**
         * Add extra horizontal gap.
         */
        fun addGap(gap: Int): HStack {
            currentX += gap
            return this
        }

        /**
         * Get total width of all items added.
         */
        fun getWidth(): Int {
            if (items.isEmpty()) return 0
            return items.last().right() - x
        }

        /**
         * Get current X position.
         */
        fun getCurrentX(): Int = currentX - spacing

        /**
         * Get the next X position where an item would be placed.
         */
        fun getNextX(): Int = currentX
    }

    /**
     * Grid layout - items are placed in rows and columns.
     */
    class Grid(
        private val x: Int,
        private val y: Int,
        private val columns: Int,
        private val itemWidth: Int,
        private val itemHeight: Int,
        private val spacingX: Int,
        private val spacingY: Int
    ) {
        private var count = 0

        /**
         * Get the box for the next item in the grid.
         */
        fun next(): Box {
            val col = count % columns
            val row = count / columns
            val itemX = x + col * (itemWidth + spacingX)
            val itemY = y + row * (itemHeight + spacingY)
            count++
            return Box(itemX, itemY, itemWidth, itemHeight)
        }

        /**
         * Get total height of the grid based on items added.
         */
        fun getHeight(): Int {
            if (count == 0) return 0
            val rows = (count + columns - 1) / columns
            return rows * itemHeight + (rows - 1) * spacingY
        }

        /**
         * Get the number of rows in the grid.
         */
        fun getRows(): Int = (count + columns - 1) / columns
    }

    /**
     * Panel with padding - represents a box with a content area inside.
     */
    class Panel(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        private val padding: Int
    ) {
        val total: Box = Box(x, y, width, height)
        val content: Box = Box(x + padding, y + padding, width - padding * 2, height - padding * 2)

        /**
         * Create a VStack inside the content area.
         */
        fun vstack(spacing: Int): VStack =
            VStack(content.x, content.y, content.width, spacing)

        /**
         * Create an HStack inside the content area.
         */
        fun hstack(spacing: Int): HStack =
            HStack(content.x, content.y, content.height, spacing)
    }

    // === Factory Functions ===

    fun box(x: Int, y: Int, width: Int, height: Int): Box =
        Box(x, y, width, height)

    fun vstack(x: Int, y: Int, width: Int, spacing: Int): VStack =
        VStack(x, y, width, spacing)

    fun hstack(x: Int, y: Int, height: Int, spacing: Int): HStack =
        HStack(x, y, height, spacing)

    fun grid(x: Int, y: Int, columns: Int, itemWidth: Int, itemHeight: Int, spacingX: Int, spacingY: Int): Grid =
        Grid(x, y, columns, itemWidth, itemHeight, spacingX, spacingY)

    fun panel(x: Int, y: Int, width: Int, height: Int, padding: Int): Panel =
        Panel(x, y, width, height, padding)
}
