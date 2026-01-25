package xyz.nim.civutils.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack
import xyz.nim.civutils.utils.ItemUtils
import xyz.nim.lib.ui.NlibTheme

/**
 * Renders an item like an inventory slot with hover tooltip support.
 * Use for inline item references and recipe displays.
 */
class ItemSlotWidget(
    private val itemId: String,
    private val count: Int = 1,
    private val size: SlotSize = SlotSize.NORMAL
) {
    enum class SlotSize(val pixels: Int, val itemScale: Float) {
        SMALL(14, 0.75f),   // For inline text
        NORMAL(18, 1.0f),   // Standard inventory slot
        LARGE(32, 2.0f)     // For page headers
    }

    private var lastX: Int = 0
    private var lastY: Int = 0
    private var cachedStack: ItemStack? = null
    private var stackResolved = false

    /**
     * Get the ItemStack, resolving from ID if needed.
     */
    private fun getStack(): ItemStack {
        if (!stackResolved) {
            cachedStack = ItemUtils.createStack(itemId, count)
            stackResolved = true
        }
        return cachedStack ?: ItemStack.EMPTY
    }

    /**
     * Render the item slot at the given position.
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
        val stack = getStack()
        val slotSize = size.pixels

        // Background
        if (renderBackground) {
            val bgColor = if (isHovered(mouseX, mouseY)) 0x60FFFFFF else 0x40000000
            guiGraphics.fill(x, y, x + slotSize, y + slotSize, bgColor)
            guiGraphics.renderOutline(x, y, slotSize, slotSize, 0x40FFFFFF)
        }

        if (stack.isEmpty) {
            // Render placeholder for unknown items
            renderMissingItem(guiGraphics, x, y)
        } else {
            // Render the actual item (always at 16x16, centered in slot)
            val mc = Minecraft.getInstance()
            val itemX = x + (slotSize - 16) / 2
            val itemY = y + (slotSize - 16) / 2

            guiGraphics.renderItem(stack, itemX, itemY)

            // Render count decoration
            if (count > 1) {
                guiGraphics.renderItemDecorations(mc.font, stack, itemX, itemY)
            }
        }

        return slotSize
    }

    /**
     * Render a placeholder for missing/unknown items.
     */
    private fun renderMissingItem(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val mc = Minecraft.getInstance()
        val slotSize = size.pixels
        val centerX = x + slotSize / 2
        val centerY = y + slotSize / 2

        // Draw a question mark
        guiGraphics.drawString(
            mc.font,
            "?",
            centerX - mc.font.width("?") / 2,
            centerY - mc.font.lineHeight / 2,
            NlibTheme.TEXT_SECONDARY,
            false
        )
    }

    /**
     * Check if the slot is currently hovered.
     */
    fun isHovered(mouseX: Int, mouseY: Int): Boolean {
        val slotSize = size.pixels
        return mouseX >= lastX && mouseX < lastX + slotSize &&
                mouseY >= lastY && mouseY < lastY + slotSize
    }

    /**
     * Render the item tooltip. Call this in the overlay pass (after content).
     */
    fun renderTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!isHovered(mouseX, mouseY)) return

        val stack = getStack()
        val mc = Minecraft.getInstance()

        if (stack.isEmpty) {
            // Show item ID for missing items using custom tooltip
            guiGraphics.drawTooltip(listOf("Unknown item:", itemId), mouseX, mouseY)
        } else {
            // Get the tooltip lines from the item stack and render
            val tooltipLines = stack.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.EMPTY,
                mc.player,
                net.minecraft.world.item.TooltipFlag.NORMAL
            )
            val textLines = tooltipLines.map { it.string }
            guiGraphics.drawTooltip(textLines, mouseX, mouseY)
        }
    }

    /**
     * Get the item ID this slot represents.
     */
    fun getItemId(): String = itemId

    /**
     * Get the slot bounds for click detection.
     */
    fun getBounds(): SlotBounds = SlotBounds(lastX, lastY, size.pixels, size.pixels, itemId)

    data class SlotBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val itemId: String
    ) {
        fun contains(mouseX: Int, mouseY: Int): Boolean =
            mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }
}

/**
 * Manages a collection of item slots for batch tooltip rendering and click handling.
 */
class ItemSlotManager {
    private val slots = mutableListOf<ItemSlotWidget>()
    private val bounds = mutableListOf<ItemSlotWidget.SlotBounds>()

    fun clear() {
        slots.clear()
        bounds.clear()
    }

    fun addSlot(slot: ItemSlotWidget) {
        slots.add(slot)
    }

    fun recordBounds(slot: ItemSlotWidget) {
        bounds.add(slot.getBounds())
    }

    /**
     * Render tooltip for the hovered slot, if any.
     */
    fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        for (slot in slots) {
            if (slot.isHovered(mouseX, mouseY)) {
                slot.renderTooltip(guiGraphics, mouseX, mouseY)
                return
            }
        }
    }

    /**
     * Get the item ID at the given position, if any.
     */
    fun getItemAt(mouseX: Int, mouseY: Int): String? {
        return bounds.find { it.contains(mouseX, mouseY) }?.itemId
    }
}
