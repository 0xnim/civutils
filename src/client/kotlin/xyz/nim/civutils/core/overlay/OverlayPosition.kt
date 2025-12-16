package xyz.nim.civutils.core.overlay

import net.minecraft.client.MinecraftClient

/**
 * Horizontal alignment options.
 */
enum class HorizontalAlignment {
    LEFT,
    CENTER,
    RIGHT
}

/**
 * Vertical alignment options.
 */
enum class VerticalAlignment {
    TOP,
    MIDDLE,
    BOTTOM
}

/**
 * Anchor sections - the screen is divided into a 3x3 grid.
 * Overlays are positioned relative to one of these 9 sections.
 *
 * ```
 * +-------------+-------------+-------------+
 * | TOP_LEFT    | TOP_MIDDLE  | TOP_RIGHT   |
 * +-------------+-------------+-------------+
 * | MIDDLE_LEFT |   MIDDLE    | MIDDLE_RIGHT|
 * +-------------+-------------+-------------+
 * | BOTTOM_LEFT |BOTTOM_MIDDLE| BOTTOM_RIGHT|
 * +-------------+-------------+-------------+
 * ```
 */
enum class AnchorSection(
    val horizontalAnchor: HorizontalAlignment,
    val verticalAnchor: VerticalAlignment
) {
    TOP_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.TOP),
    TOP_MIDDLE(HorizontalAlignment.CENTER, VerticalAlignment.TOP),
    TOP_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.TOP),

    MIDDLE_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.MIDDLE),
    MIDDLE(HorizontalAlignment.CENTER, VerticalAlignment.MIDDLE),
    MIDDLE_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.MIDDLE),

    BOTTOM_LEFT(HorizontalAlignment.LEFT, VerticalAlignment.BOTTOM),
    BOTTOM_MIDDLE(HorizontalAlignment.CENTER, VerticalAlignment.BOTTOM),
    BOTTOM_RIGHT(HorizontalAlignment.RIGHT, VerticalAlignment.BOTTOM);

    /**
     * Get the X coordinate of this anchor section on screen.
     */
    fun getAnchorX(screenWidth: Int): Int {
        return when (horizontalAnchor) {
            HorizontalAlignment.LEFT -> 0
            HorizontalAlignment.CENTER -> screenWidth / 2
            HorizontalAlignment.RIGHT -> screenWidth
        }
    }

    /**
     * Get the Y coordinate of this anchor section on screen.
     */
    fun getAnchorY(screenHeight: Int): Int {
        return when (verticalAnchor) {
            VerticalAlignment.TOP -> 0
            VerticalAlignment.MIDDLE -> screenHeight / 2
            VerticalAlignment.BOTTOM -> screenHeight
        }
    }
}

/**
 * Defines the position of an overlay on screen.
 *
 * @param offsetX Horizontal offset from the anchor point
 * @param offsetY Vertical offset from the anchor point
 * @param horizontalAlignment How the overlay aligns horizontally within its bounds
 * @param verticalAlignment How the overlay aligns vertically within its bounds
 * @param anchorSection Which section of the screen to anchor to
 */
data class OverlayPosition(
    var offsetX: Int = 0,
    var offsetY: Int = 0,
    var horizontalAlignment: HorizontalAlignment = HorizontalAlignment.LEFT,
    var verticalAlignment: VerticalAlignment = VerticalAlignment.TOP,
    var anchorSection: AnchorSection = AnchorSection.TOP_LEFT
) {
    /**
     * Calculate the actual render X position on screen.
     */
    fun getRenderX(screenWidth: Int, overlayWidth: Int): Int {
        val anchorX = anchorSection.getAnchorX(screenWidth)

        // Apply alignment offset
        val alignmentOffset = when (horizontalAlignment) {
            HorizontalAlignment.LEFT -> 0
            HorizontalAlignment.CENTER -> -overlayWidth / 2
            HorizontalAlignment.RIGHT -> -overlayWidth
        }

        return anchorX + offsetX + alignmentOffset
    }

    /**
     * Calculate the actual render Y position on screen.
     */
    fun getRenderY(screenHeight: Int, overlayHeight: Int): Int {
        val anchorY = anchorSection.getAnchorY(screenHeight)

        // Apply alignment offset
        val alignmentOffset = when (verticalAlignment) {
            VerticalAlignment.TOP -> 0
            VerticalAlignment.MIDDLE -> -overlayHeight / 2
            VerticalAlignment.BOTTOM -> -overlayHeight
        }

        return anchorY + offsetY + alignmentOffset
    }

    companion object {
        /**
         * Create a position anchored to the top-left corner.
         */
        fun topLeft(offsetX: Int = 5, offsetY: Int = 5) = OverlayPosition(
            offsetX = offsetX,
            offsetY = offsetY,
            horizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlignment = VerticalAlignment.TOP,
            anchorSection = AnchorSection.TOP_LEFT
        )

        /**
         * Create a position anchored to the top-right corner.
         */
        fun topRight(offsetX: Int = -5, offsetY: Int = 5) = OverlayPosition(
            offsetX = offsetX,
            offsetY = offsetY,
            horizontalAlignment = HorizontalAlignment.RIGHT,
            verticalAlignment = VerticalAlignment.TOP,
            anchorSection = AnchorSection.TOP_RIGHT
        )

        /**
         * Create a position anchored to the bottom-left corner.
         */
        fun bottomLeft(offsetX: Int = 5, offsetY: Int = -5) = OverlayPosition(
            offsetX = offsetX,
            offsetY = offsetY,
            horizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlignment = VerticalAlignment.BOTTOM,
            anchorSection = AnchorSection.BOTTOM_LEFT
        )

        /**
         * Create a position anchored to the bottom-middle.
         */
        fun bottomCenter(offsetX: Int = 0, offsetY: Int = -50) = OverlayPosition(
            offsetX = offsetX,
            offsetY = offsetY,
            horizontalAlignment = HorizontalAlignment.CENTER,
            verticalAlignment = VerticalAlignment.BOTTOM,
            anchorSection = AnchorSection.BOTTOM_MIDDLE
        )

        /**
         * Create a position centered on screen.
         */
        fun center(offsetX: Int = 0, offsetY: Int = 0) = OverlayPosition(
            offsetX = offsetX,
            offsetY = offsetY,
            horizontalAlignment = HorizontalAlignment.CENTER,
            verticalAlignment = VerticalAlignment.MIDDLE,
            anchorSection = AnchorSection.MIDDLE
        )

        /**
         * Create a position anchored to the middle-left.
         */
        fun middleLeft(offsetX: Int = 5, offsetY: Int = 0) = OverlayPosition(
            offsetX = offsetX,
            offsetY = offsetY,
            horizontalAlignment = HorizontalAlignment.LEFT,
            verticalAlignment = VerticalAlignment.MIDDLE,
            anchorSection = AnchorSection.MIDDLE_LEFT
        )

        /**
         * Create a position anchored to the top-center.
         */
        fun topCenter(offsetX: Int = 0, offsetY: Int = 5) = OverlayPosition(
            offsetX = offsetX,
            offsetY = offsetY,
            horizontalAlignment = HorizontalAlignment.CENTER,
            verticalAlignment = VerticalAlignment.TOP,
            anchorSection = AnchorSection.TOP_MIDDLE
        )
    }
}

/**
 * Defines the size of an overlay.
 */
data class OverlaySize(
    var width: Int = 100,
    var height: Int = 20
) {
    companion object {
        fun of(width: Int, height: Int) = OverlaySize(width, height)
    }
}
