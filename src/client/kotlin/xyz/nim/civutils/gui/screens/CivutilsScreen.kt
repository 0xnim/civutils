package xyz.nim.civutils.gui.screens

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.Drawable
import net.minecraft.client.gui.screen.Screen
import net.minecraft.text.Text
import xyz.nim.lib.ui.ConfirmDialog
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.lib.ui.ResponsiveLayout
import xyz.nim.lib.ui.ToastManager

/**
 * Base screen class for CivUtils screens.
 * Provides toast notifications, confirm dialogs, and themed rendering.
 */
abstract class CivutilsScreen(title: Text) : Screen(title) {

    protected val toastManager = ToastManager()
    protected var confirmDialog: ConfirmDialog? = null
    protected lateinit var layout: ResponsiveLayout

    override fun init() {
        super.init()
        layout = ResponsiveLayout(width, height)
    }

    override fun shouldPause(): Boolean = false

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        confirmDialog?.let { dialog ->
            if (dialog.isVisible) {
                return dialog.keyPressed(keyCode, scanCode, modifiers)
            }
        }

        // ESC to close
        if (keyCode == 256) { // GLFW_KEY_ESCAPE
            close()
            return true
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        confirmDialog?.let { dialog ->
            if (dialog.isVisible) {
                return dialog.mouseClicked(mouseX, mouseY, button)
            }
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun render(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // 1. Render dark background
        context.fill(0, 0, width, height, 0xC0101010.toInt())

        // 2. Render panels (subclasses override this)
        renderPanels(context, mouseX, mouseY, delta)

        // 3. Render all widgets
        for (element in children()) {
            if (element is Drawable) {
                element.render(context, mouseX, mouseY, delta)
            }
        }

        // 4. Render overlays (subclass-specific content on top of widgets)
        renderOverlays(context, mouseX, mouseY, delta)

        // 5. Toasts and dialogs on top of everything
        toastManager.render(context, textRenderer, width, height)
        confirmDialog?.let { dialog ->
            if (dialog.isVisible) {
                dialog.render(context, textRenderer, mouseX, mouseY)
            }
        }
    }

    /**
     * Override to render panel backgrounds before widgets.
     */
    protected open fun renderPanels(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Default: no panels
    }

    /**
     * Override to render overlays on top of widgets.
     */
    protected open fun renderOverlays(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Default: no overlays
    }

    // === Helper Methods ===

    /**
     * Draw a themed panel background.
     */
    protected fun drawPanel(context: DrawContext, x: Int, y: Int, w: Int, h: Int) {
        context.fill(x, y, x + w, y + h, NlibTheme.PANEL_BG)
        drawBorder(context, x, y, w, h, NlibTheme.PANEL_BORDER)
    }

    /**
     * Draw a themed panel with header.
     */
    protected fun drawPanelWithHeader(context: DrawContext, x: Int, y: Int, w: Int, h: Int, title: String) {
        drawPanel(context, x, y, w, h)
        val headerH = layout.headerHeight
        context.fill(x + 1, y + 1, x + w - 1, y + headerH, NlibTheme.HEADER_BG)
        context.drawText(textRenderer, title, x + layout.padding, y + (headerH - 8) / 2, NlibTheme.TEXT_PRIMARY, false)
    }

    /**
     * Draw a border around a rectangle.
     */
    protected fun drawBorder(context: DrawContext, x: Int, y: Int, w: Int, h: Int, color: Int) {
        context.drawHorizontalLine(x, x + w - 1, y, color)
        context.drawHorizontalLine(x, x + w - 1, y + h - 1, color)
        context.drawVerticalLine(x, y, y + h - 1, color)
        context.drawVerticalLine(x + w - 1, y, y + h - 1, color)
    }

    /**
     * Show a confirmation dialog.
     */
    protected fun confirm(title: String, message: String, onConfirm: () -> Unit) {
        confirmDialog = ConfirmDialog(title, message, onConfirm, {})
        confirmDialog?.show(width, height) { addDrawableChild(it) }
    }

    /**
     * Show a confirmation dialog with cancel callback.
     */
    protected fun confirm(title: String, message: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
        confirmDialog = ConfirmDialog(title, message, onConfirm, onCancel)
        confirmDialog?.show(width, height) { addDrawableChild(it) }
    }

    // Convenience accessors for layout properties
    protected fun margin(): Int = layout.margin
    protected fun padding(): Int = layout.padding
    protected fun spacing(): Int = layout.spacing
}
