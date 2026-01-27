package xyz.nim.civutils.gui.widgets

import net.minecraft.client.gui.GuiGraphics
import xyz.nim.civutils.data.handbook.ItemDefinition

/**
 * A widget that cycles through multiple items on a timer.
 * All cycling slots share the same timing via System.currentTimeMillis(),
 * so they appear synchronized across the UI.
 */
class CyclingItemSlotWidget(
    private val items: List<ItemDefinition>,
    private val count: Int = 1,
    private val size: ItemSlotWidget.SlotSize = ItemSlotWidget.SlotSize.NORMAL,
    private val cycleIntervalMs: Long = 1000L
) {
    private var lastWidget: ItemSlotWidget? = null
    private var lastX: Int = 0
    private var lastY: Int = 0

    /**
     * Get the current cycle index based on system time.
     * All instances will return the same index at the same moment.
     */
    private fun getCurrentIndex(): Int {
        if (items.isEmpty()) return 0
        return ((System.currentTimeMillis() / cycleIntervalMs) % items.size).toInt()
    }

    /**
     * Get the currently displayed item.
     */
    fun getCurrentItem(): ItemDefinition? = items.getOrNull(getCurrentIndex())

    /**
     * Render the widget at the given position.
     * @return The width consumed
     */
    fun render(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        renderBackground: Boolean = true
    ): Int {
        lastX = x
        lastY = y

        if (items.isEmpty()) {
            return renderEmptySlot(guiGraphics, x, y)
        }

        val currentItem = items[getCurrentIndex()]
        val widget = ItemSlotWidget.fromItemDefinition(currentItem, count, size)
        lastWidget = widget

        return widget.render(guiGraphics, x, y, mouseX, mouseY, renderBackground)
    }

    /**
     * Render an empty slot when no items are available.
     */
    private fun renderEmptySlot(guiGraphics: GuiGraphics, x: Int, y: Int): Int {
        val slotSize = size.pixels
        guiGraphics.fill(x, y, x + slotSize, y + slotSize, 0x40000000)
        guiGraphics.renderOutline(x, y, slotSize, slotSize, 0x40FFFFFF)
        return slotSize
    }

    /**
     * Check if this widget is currently hovered.
     */
    fun isHovered(mouseX: Int, mouseY: Int): Boolean {
        return lastWidget?.isHovered(mouseX, mouseY) ?: false
    }

    /**
     * Render tooltip for the current item.
     */
    fun renderTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        lastWidget?.renderTooltip(guiGraphics, mouseX, mouseY)
    }

    /**
     * Get the item ID for navigation (the currently displayed item's ID).
     */
    fun getItemId(): String? = lastWidget?.getItemId()

    /**
     * Get the bounds for click detection.
     */
    fun getBounds(): ItemSlotWidget.SlotBounds? = lastWidget?.getBounds()
}
