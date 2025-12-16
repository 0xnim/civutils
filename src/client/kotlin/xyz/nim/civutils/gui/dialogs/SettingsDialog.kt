package xyz.nim.civutils.gui.dialogs

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import xyz.nim.civutils.gui.theme.CivutilsTheme

/**
 * A modal settings dialog with configurable rows.
 */
class SettingsDialog(
    private val title: String,
    private val rows: List<SettingRow>,
    private val onClose: () -> Unit = {}
) {
    companion object {
        private const val DIALOG_WIDTH = 280
        private const val DIALOG_PADDING = 12
        private const val BUTTON_HEIGHT = 20
        private const val ROW_HEIGHT = 26

        private const val BG_COLOR = 0xF0101010.toInt()
        private const val BORDER_COLOR = 0xFFAAAAAA.toInt()
        private const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        private const val LABEL_COLOR = 0xFFCCCCCC.toInt()
    }

    /**
     * A single row in the settings dialog.
     */
    class SettingRow(
        val label: String,
        private val valueSupplier: () -> String,
        private val onCycle: () -> Unit,
        private val visibilitySupplier: () -> Boolean = { true },
        private val enabledSupplier: () -> Boolean = { true }
    ) {
        internal var button: ButtonWidget? = null

        fun getCurrentValue(): String = valueSupplier()
        fun isVisible(): Boolean = visibilitySupplier()
        fun isEnabled(): Boolean = enabledSupplier()
        fun cycle() = onCycle()
    }

    private var closeButton: ButtonWidget? = null
    var isVisible: Boolean = false
        private set

    private var dialogX: Int = 0
    private var dialogY: Int = 0
    private var dialogHeight: Int = 0

    /**
     * Show the dialog centered on screen.
     */
    fun show(screenWidth: Int, screenHeight: Int, addButton: (ButtonWidget) -> ButtonWidget) {
        isVisible = true

        dialogHeight = DIALOG_PADDING * 2 + 14 + rows.size * ROW_HEIGHT + BUTTON_HEIGHT + 8
        dialogX = (screenWidth - DIALOG_WIDTH) / 2
        dialogY = (screenHeight - dialogHeight) / 2

        var currentY = dialogY + DIALOG_PADDING + 18
        val buttonWidth = DIALOG_WIDTH - DIALOG_PADDING * 2 - 80
        val buttonX = dialogX + DIALOG_PADDING + 75

        for (row in rows) {
            row.button = ButtonWidget.builder(Text.literal(row.getCurrentValue())) { btn ->
                row.cycle()
                btn.message = Text.literal(row.getCurrentValue())
            }.dimensions(buttonX, currentY, buttonWidth, BUTTON_HEIGHT).build().also {
                it.visible = row.isVisible()
                it.active = row.isEnabled()
            }
            addButton(row.button!!)
            currentY += ROW_HEIGHT
        }

        val closeY = dialogY + dialogHeight - DIALOG_PADDING - BUTTON_HEIGHT
        closeButton = ButtonWidget.builder(Text.literal("Done")) {
            hide()
            onClose()
        }.dimensions(dialogX + (DIALOG_WIDTH - 80) / 2, closeY, 80, BUTTON_HEIGHT).build()
        addButton(closeButton!!)
    }

    /**
     * Hide the dialog.
     */
    fun hide() {
        isVisible = false
        closeButton?.visible = false
        for (row in rows) {
            row.button?.visible = false
        }
    }

    /**
     * Update the display of all rows.
     */
    fun updateRows() {
        for (row in rows) {
            row.button?.let { btn ->
                btn.message = Text.literal(row.getCurrentValue())
                btn.visible = isVisible && row.isVisible()
                btn.active = row.isEnabled()
            }
        }
    }

    /**
     * Render the dialog.
     */
    fun render(context: DrawContext, textRenderer: TextRenderer, mouseX: Int, mouseY: Int) {
        if (!isVisible) return

        // Dark overlay
        context.fill(0, 0, context.scaledWindowWidth, context.scaledWindowHeight, 0x80000000.toInt())

        // Dialog background
        context.fill(dialogX, dialogY, dialogX + DIALOG_WIDTH, dialogY + dialogHeight, BG_COLOR)
        context.drawBorder(dialogX, dialogY, DIALOG_WIDTH, dialogHeight, BORDER_COLOR)

        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, dialogX + DIALOG_WIDTH / 2, dialogY + DIALOG_PADDING, TITLE_COLOR)

        // Labels
        val labelX = dialogX + DIALOG_PADDING
        var currentY = dialogY + DIALOG_PADDING + 18 + 5

        for (row in rows) {
            if (row.isVisible()) {
                context.drawText(textRenderer, row.label, labelX, currentY, LABEL_COLOR, false)
            }
            currentY += ROW_HEIGHT
        }

        // Render row buttons
        for (row in rows) {
            row.button?.let { btn ->
                if (btn.visible) {
                    btn.render(context, mouseX, mouseY, 0f)
                }
            }
        }

        // Render close button
        closeButton?.render(context, mouseX, mouseY, 0f)
    }

    /**
     * Handle mouse click.
     */
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isVisible) return false

        for (row in rows) {
            row.button?.let { btn ->
                if (btn.visible && btn.mouseClicked(mouseX, mouseY, button)) {
                    return true
                }
            }
        }

        closeButton?.let {
            if (it.mouseClicked(mouseX, mouseY, button)) return true
        }

        // Consume click
        return true
    }

    /**
     * Handle key press.
     */
    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!isVisible) return false

        // Escape or Enter to close
        if (keyCode == 256 || keyCode == 257 || keyCode == 335) {
            hide()
            onClose()
            return true
        }

        return true
    }
}
