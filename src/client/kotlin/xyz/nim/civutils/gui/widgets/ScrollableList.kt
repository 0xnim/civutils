package xyz.nim.civutils.gui.widgets

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.Element
import net.minecraft.client.gui.Selectable
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder
import kotlin.math.max
import kotlin.math.min

/**
 * A scrollable list of entries.
 */
class ScrollableList<T>(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val itemHeight: Int,
    private val renderEntry: (DrawContext, T, Int, Int, Int, Int, Boolean, Boolean) -> Unit,
    private val onEntryClick: ((T, Int) -> Unit)? = null
) : Drawable, Element, Selectable {

    private var entries: List<T> = emptyList()
    private var scrollOffset: Double = 0.0
    private var selectedIndex: Int = -1
    private var hoveredIndex: Int = -1
    private var isDraggingScrollbar = false

    fun setEntries(newEntries: List<T>) {
        entries = newEntries
        scrollOffset = 0.0
        selectedIndex = -1
    }

    fun getSelectedEntry(): T? {
        return if (selectedIndex in entries.indices) entries[selectedIndex] else null
    }

    fun setSelectedIndex(index: Int) {
        selectedIndex = index
    }

    private val maxScroll: Double
        get() = max(0.0, (entries.size * itemHeight - height).toDouble())

    private val scrollbarHeight: Int
        get() {
            if (entries.isEmpty()) return height
            val visibleRatio = height.toDouble() / (entries.size * itemHeight)
            return max(20, (height * visibleRatio).toInt())
        }

    private val scrollbarY: Int
        get() {
            if (maxScroll <= 0) return y
            val scrollRatio = scrollOffset / maxScroll
            return y + ((height - scrollbarHeight) * scrollRatio).toInt()
        }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val mc = MinecraftClient.getInstance()

        // Background
        context.fill(x, y, x + width, y + height, Colors.BACKGROUND)

        // Enable scissor to clip content
        context.enableScissor(x, y, x + width - 10, y + height)

        // Render visible entries
        var entryY = y - scrollOffset.toInt()
        hoveredIndex = -1

        for ((index, entry) in entries.withIndex()) {
            if (entryY + itemHeight > y && entryY < y + height) {
                val isSelected = index == selectedIndex
                val isHovered = mouseX >= x && mouseX < x + width - 10 &&
                        mouseY >= entryY && mouseY < entryY + itemHeight &&
                        mouseY >= y && mouseY < y + height

                if (isHovered) hoveredIndex = index

                renderEntry(context, entry, x, entryY, width - 12, itemHeight, isSelected, isHovered)
            }
            entryY += itemHeight
        }

        context.disableScissor()

        // Render scrollbar if needed
        if (maxScroll > 0) {
            // Scrollbar track
            context.fill(x + width - 8, y, x + width, y + height, Colors.BACKGROUND_LIGHT)

            // Scrollbar thumb
            val thumbColor = if (isDraggingScrollbar) Colors.ACCENT else Colors.TEXT_SECONDARY
            context.fill(x + width - 7, scrollbarY, x + width - 1, scrollbarY + scrollbarHeight, thumbColor)
        }

        // Border
        context.drawBorder(x, y, width, height, Colors.TEXT_SECONDARY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false

        // Check scrollbar click
        if (mouseX >= x + width - 10 && maxScroll > 0) {
            isDraggingScrollbar = true
            return true
        }

        // Check entry click
        if (hoveredIndex >= 0 && button == 0) {
            selectedIndex = hoveredIndex
            onEntryClick?.invoke(entries[hoveredIndex], hoveredIndex)
            return true
        }

        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        isDraggingScrollbar = false
        return false
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar && maxScroll > 0) {
            val scrollRange = height - scrollbarHeight
            if (scrollRange > 0) {
                scrollOffset += (deltaY / scrollRange) * maxScroll
                scrollOffset = scrollOffset.coerceIn(0.0, maxScroll)
            }
            return true
        }
        return false
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        if (!isMouseOver(mouseX, mouseY)) return false

        scrollOffset -= verticalAmount * 20
        scrollOffset = scrollOffset.coerceIn(0.0, maxScroll)
        return true
    }

    override fun isMouseOver(mouseX: Double, mouseY: Double): Boolean {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    override fun setFocused(focused: Boolean) {}
    override fun isFocused(): Boolean = false

    override fun getType(): Selectable.SelectionType = Selectable.SelectionType.NONE
    override fun appendNarrations(builder: NarrationMessageBuilder) {}
}
