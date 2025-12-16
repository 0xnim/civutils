package xyz.nim.civutils.gui.dialogs

import net.minecraft.client.MinecraftClient
import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import xyz.nim.civutils.gui.theme.CivutilsTheme

/**
 * A modal confirmation dialog with Confirm and Cancel buttons.
 */
class ConfirmDialog(
    private val title: String,
    private val message: String,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit = {}
) {
    companion object {
        private const val DIALOG_WIDTH = 250
        private const val DIALOG_PADDING = 12
        private const val BUTTON_HEIGHT = 20
        private const val BUTTON_WIDTH = 80
        private const val BUTTON_SPACING = 8

        private const val BG_COLOR = 0xF0101010.toInt()
        private const val BORDER_COLOR = 0xFFAAAAAA.toInt()
        private const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        private const val MESSAGE_COLOR = 0xFFCCCCCC.toInt()
    }

    private var confirmButton: ButtonWidget? = null
    private var cancelButton: ButtonWidget? = null
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

        val textRenderer = MinecraftClient.getInstance().textRenderer
        val messageLines = maxOf(1, minOf(4,
            kotlin.math.ceil(textRenderer.getWidth(message) / (DIALOG_WIDTH - DIALOG_PADDING * 2).toDouble()).toInt()
        ))

        dialogHeight = DIALOG_PADDING * 3 + 12 + messageLines * 10 + BUTTON_HEIGHT
        dialogX = (screenWidth - DIALOG_WIDTH) / 2
        dialogY = (screenHeight - dialogHeight) / 2

        val buttonY = dialogY + dialogHeight - DIALOG_PADDING - BUTTON_HEIGHT
        val buttonsWidth = BUTTON_WIDTH * 2 + BUTTON_SPACING
        val buttonStartX = dialogX + (DIALOG_WIDTH - buttonsWidth) / 2

        confirmButton = ButtonWidget.builder(Text.literal("Confirm")) {
            hide()
            onConfirm()
        }.dimensions(buttonStartX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build()

        cancelButton = ButtonWidget.builder(Text.literal("Cancel")) {
            hide()
            onCancel()
        }.dimensions(buttonStartX + BUTTON_WIDTH + BUTTON_SPACING, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT).build()

        addButton(confirmButton!!)
        addButton(cancelButton!!)
    }

    /**
     * Hide the dialog.
     */
    fun hide() {
        isVisible = false
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

        // Message (word-wrapped)
        var messageY = dialogY + DIALOG_PADDING + 14
        val maxWidth = DIALOG_WIDTH - DIALOG_PADDING * 2
        var remaining = message
        var lines = 0

        while (remaining.isNotEmpty() && lines < 4) {
            val line = textRenderer.trimToWidth(remaining, maxWidth)
            context.drawCenteredTextWithShadow(textRenderer, line, dialogX + DIALOG_WIDTH / 2, messageY, MESSAGE_COLOR)
            remaining = remaining.substring(line.length).trim()
            messageY += 10
            lines++
        }

        // Render buttons
        confirmButton?.render(context, mouseX, mouseY, 0f)
        cancelButton?.render(context, mouseX, mouseY, 0f)
    }

    /**
     * Handle mouse click.
     */
    fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (!isVisible) return false

        confirmButton?.let {
            if (it.mouseClicked(mouseX, mouseY, button)) return true
        }
        cancelButton?.let {
            if (it.mouseClicked(mouseX, mouseY, button)) return true
        }

        // Consume click to prevent interaction with elements behind
        return true
    }

    /**
     * Handle key press.
     */
    fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        if (!isVisible) return false

        // Enter to confirm
        if (keyCode == 257 || keyCode == 335) { // GLFW_KEY_ENTER or GLFW_KEY_KP_ENTER
            hide()
            onConfirm()
            return true
        }

        // Escape to cancel
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            hide()
            onCancel()
            return true
        }

        return true
    }
}
