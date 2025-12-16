package xyz.nim.civutils.gui.widgets

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import xyz.nim.civutils.gui.theme.CivutilsTheme
import kotlin.math.max
import kotlin.math.min

/**
 * A dropdown widget for selecting from a list of options.
 */
class DropdownWidget(
    private val client: MinecraftClient,
    private val x: Int,
    private val y: Int,
    private val width: Int,
    private val height: Int,
    private val options: List<DropdownOption>,
    private val onSelect: ((String) -> Unit)? = null,
    private val maxVisibleOptions: Int = 5
) {
    /**
     * A single option in the dropdown.
     */
    data class DropdownOption(
        val value: String,
        val label: String
    )

    companion object {
        private const val OPTION_HEIGHT = 20
        private const val SCROLLBAR_WIDTH = 6
    }

    private var button: ButtonWidget
    private var expanded: Boolean = false
    private var selectedIndex: Int = 0
    private var selectedValue: String = options.firstOrNull()?.value ?: ""
    private var scrollOffset: Int = 0

    init {
        button = createButton()
    }

    private fun createButton(): ButtonWidget {
        val label = if (options.isEmpty()) "Select..." else options[selectedIndex].label
        return ButtonWidget.builder(Text.literal("$label \u25BC")) { // ▼
            expanded = !expanded
            updateButtonText()
        }.dimensions(x, y, width, height).build()
    }

    /**
     * Set the selected value by its value string.
     */
    fun setSelected(value: String) {
        val index = options.indexOfFirst { it.value == value }
        if (index >= 0) {
            selectedIndex = index
            selectedValue = value
            updateButtonText()
        }
    }

    /**
     * Get the currently selected value.
     */
    fun getSelectedValue(): String = selectedValue

    /**
     * Get the button widget for adding to the screen.
     */
    fun getButton(): ButtonWidget = button

    private fun updateButtonText() {
        if (selectedIndex in options.indices) {
            val label = options[selectedIndex].label
            val arrow = if (expanded) "\u25B2" else "\u25BC" // ▲ or ▼
            button.message = Text.literal("$label $arrow")
        }
    }

    /**
     * Render the dropdown (only the expanded portion).
     */
    fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        if (!expanded) return

        val visibleCount = min(options.size, maxVisibleOptions)
        val dropdownHeight = visibleCount * OPTION_HEIGHT
        val dropdownY = y + height
        val needsScroll = options.size > maxVisibleOptions

        // Draw background
        context.fill(x, dropdownY, x + width, dropdownY + dropdownHeight, CivutilsTheme.PANEL_BG)
        context.drawBorder(x, dropdownY, width, dropdownHeight, CivutilsTheme.PANEL_BORDER)

        // Clamp scroll offset
        val maxScroll = max(0, options.size - maxVisibleOptions)
        scrollOffset = scrollOffset.coerceIn(0, maxScroll)

        // Render visible options
        for (i in 0 until visibleCount) {
            val optionIndex = i + scrollOffset
            if (optionIndex >= options.size) break

            val optionY = dropdownY + i * OPTION_HEIGHT
            val optionRight = x + width - if (needsScroll) SCROLLBAR_WIDTH else 0

            val isHovered = mouseX >= x && mouseX < optionRight &&
                    mouseY >= optionY && mouseY < optionY + OPTION_HEIGHT

            if (isHovered) {
                context.fill(x + 1, optionY + 1, optionRight - 1, optionY + OPTION_HEIGHT - 1, CivutilsTheme.HOVER)
            }

            val option = options[optionIndex]
            val textColor = if (optionIndex == selectedIndex) CivutilsTheme.SELECTED else CivutilsTheme.TEXT_PRIMARY
            context.drawText(client.textRenderer, option.label, x + 5, optionY + 6, textColor, false)
        }

        // Render scrollbar
        if (needsScroll) {
            val scrollbarX = x + width - SCROLLBAR_WIDTH
            val scrollbarTrackHeight = dropdownHeight
            val scrollbarHeight = max(20, (visibleCount * dropdownHeight) / options.size)
            val scrollbarY = dropdownY + (scrollOffset.toFloat() / maxScroll * (scrollbarTrackHeight - scrollbarHeight)).toInt()

            context.fill(scrollbarX, dropdownY, scrollbarX + SCROLLBAR_WIDTH, dropdownY + dropdownHeight, CivutilsTheme.HEADER_BG)
            context.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, scrollbarY + scrollbarHeight, CivutilsTheme.TEXT_MUTED)
        }
    }

    /**
     * Handle mouse click.
     */
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!expanded) return false

        val visibleCount = min(options.size, maxVisibleOptions)
        val dropdownHeight = visibleCount * OPTION_HEIGHT
        val dropdownY = y + height

        // Check if clicked inside dropdown
        if (mouseX >= x && mouseX < x + width && mouseY >= dropdownY && mouseY < dropdownY + dropdownHeight) {
            val clickedVisibleIndex = ((mouseY - dropdownY) / OPTION_HEIGHT).toInt()
            val clickedIndex = clickedVisibleIndex + scrollOffset

            if (clickedIndex in options.indices) {
                selectedIndex = clickedIndex
                selectedValue = options[clickedIndex].value
                expanded = false
                scrollOffset = 0
                updateButtonText()
                onSelect?.invoke(selectedValue)
                return true
            }
        }

        // Click outside dropdown - close it
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= dropdownY + dropdownHeight) {
            expanded = false
            scrollOffset = 0
            updateButtonText()
            return true
        }

        return false
    }

    /**
     * Handle mouse scroll.
     */
    fun mouseScrolled(mouseX: Double, mouseY: Double, amount: Double): Boolean {
        if (!expanded) return false

        val visibleCount = min(options.size, maxVisibleOptions)
        val dropdownHeight = visibleCount * OPTION_HEIGHT
        val dropdownY = y + height

        if (mouseX >= x && mouseX < x + width && mouseY >= dropdownY && mouseY < dropdownY + dropdownHeight) {
            scrollOffset -= amount.toInt()
            val maxScroll = max(0, options.size - maxVisibleOptions)
            scrollOffset = scrollOffset.coerceIn(0, maxScroll)
            return true
        }

        return false
    }

    /**
     * Check if the dropdown is expanded.
     */
    fun isExpanded(): Boolean = expanded

    /**
     * Manually set expanded state.
     */
    fun setExpanded(expanded: Boolean) {
        this.expanded = expanded
        updateButtonText()
    }
}
