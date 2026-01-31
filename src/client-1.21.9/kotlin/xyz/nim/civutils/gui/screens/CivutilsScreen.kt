package xyz.nim.civutils.gui.screens

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.network.chat.Component
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.lib.ui.ResponsiveLayout
import xyz.nim.lib.ui.ToastManager

/**
 * Base screen class for CivUtils screens.
 * Provides toast notifications and themed rendering.
 */
abstract class CivutilsScreen(title: Component) : Screen(title) {

    protected val toastManager = ToastManager()
    protected lateinit var layout: ResponsiveLayout

    override fun init() {
        super.init()
        layout = ResponsiveLayout(width, height)
    }

    override fun isPauseScreen(): Boolean = false

    override fun keyPressed(keyEvent: KeyEvent): Boolean {
        // ESC to close
        if (keyEvent.key() == 256) { // GLFW_KEY_ESCAPE
            onClose()
            return true
        }

        return super.keyPressed(keyEvent)
    }

    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // 1. Render dark background
        guiGraphics.fill(0, 0, width, height, 0xC0101010.toInt())

        // 2. Render panels (subclasses override this)
        renderPanels(guiGraphics, mouseX, mouseY, partialTick)

        // 3. Render all widgets
        for (element in children()) {
            if (element is Renderable) {
                element.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }

        // 4. Render overlays (subclass-specific content on top of widgets)
        renderOverlays(guiGraphics, mouseX, mouseY, partialTick)

        // 5. Toasts on top of everything
        toastManager.render(guiGraphics, font, width, height)
    }

    /**
     * Override to render panel backgrounds before widgets.
     */
    protected open fun renderPanels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Default: no panels
    }

    /**
     * Override to render overlays on top of widgets.
     */
    protected open fun renderOverlays(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Default: no overlays
    }

    // === Helper Methods ===

    /**
     * Draw a themed panel background.
     */
    protected fun drawPanel(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int) {
        guiGraphics.fill(x, y, x + w, y + h, NlibTheme.PANEL_BG)
        drawBorder(guiGraphics, x, y, w, h, NlibTheme.PANEL_BORDER)
    }

    /**
     * Draw a themed panel with header.
     */
    protected fun drawPanelWithHeader(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, title: String) {
        drawPanel(guiGraphics, x, y, w, h)
        val headerH = layout.headerHeight
        guiGraphics.fill(x + 1, y + 1, x + w - 1, y + headerH, NlibTheme.HEADER_BG)
        guiGraphics.drawString(font, title, x + layout.padding, y + (headerH - 8) / 2, NlibTheme.TEXT_PRIMARY, false)
    }

    /**
     * Draw a border around a rectangle.
     */
    protected fun drawBorder(guiGraphics: GuiGraphics, x: Int, y: Int, w: Int, h: Int, color: Int) {
        guiGraphics.hLine(x, x + w - 1, y, color)
        guiGraphics.hLine(x, x + w - 1, y + h - 1, color)
        guiGraphics.vLine(x, y, y + h - 1, color)
        guiGraphics.vLine(x + w - 1, y, y + h - 1, color)
    }

    // Convenience accessors for layout properties
    protected fun margin(): Int = layout.margin
    protected fun padding(): Int = layout.padding
    protected fun spacing(): Int = layout.spacing
}
