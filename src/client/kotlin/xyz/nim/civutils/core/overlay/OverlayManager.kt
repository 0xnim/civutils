package xyz.nim.civutils.core.overlay

import net.minecraft.client.gui.GuiGraphics
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.HudRenderEvent
import xyz.nim.civutils.core.event.Subscribe

/**
 * Manages registration and rendering of all overlays.
 */
class OverlayManager {
    private val overlays = mutableMapOf<String, Overlay>()

    /**
     * When true, all overlays are hidden (but still enabled).
     * This is a temporary toggle, not persisted.
     */
    var overlaysHidden: Boolean = false
        private set

    /**
     * Toggle overlay visibility on/off.
     * Returns the new visibility state (true = hidden, false = visible).
     */
    fun toggleOverlayVisibility(): Boolean {
        overlaysHidden = !overlaysHidden
        return overlaysHidden
    }

    init {
        // Register to receive tick and render events
        CivutilsMod.eventBus.register(this)
    }

    /**
     * Get all registered overlays.
     */
    fun getOverlays(): Collection<Overlay> = overlays.values

    /**
     * Get an overlay by its ID.
     */
    fun getOverlay(id: String): Overlay? = overlays[id]

    /**
     * Register an overlay.
     */
    fun registerOverlay(overlay: Overlay) {
        if (overlays.containsKey(overlay.id)) {
            CivutilsMod.logger.warn("Overlay ${overlay.id} is already registered")
            return
        }

        overlays[overlay.id] = overlay
        overlay.registerConfigs()
        CivutilsMod.logger.debug("Registered overlay: ${overlay.id}")
    }

    /**
     * Register multiple overlays at once.
     */
    fun registerOverlays(vararg overlaysToRegister: Overlay) {
        for (overlay in overlaysToRegister) {
            registerOverlay(overlay)
        }
    }

    /**
     * Unregister an overlay.
     */
    fun unregisterOverlay(overlay: Overlay) {
        overlays.remove(overlay.id)
        CivutilsMod.configManager.unregisterOwner(overlay.id)
    }

    /**
     * Tick all overlays.
     */
    @Subscribe
    fun onClientTick(event: ClientTickEvent) {
        if (!CivutilsMod.isInGame()) return

        for (overlay in overlays.values) {
            if (overlay.enabled.value) {
                try {
                    overlay.tick()
                } catch (e: Exception) {
                    CivutilsMod.logger.error("Error ticking overlay ${overlay.id}", e)
                }
            }
        }
    }

    /**
     * Render all overlays.
     */
    @Subscribe
    fun onHudRender(event: HudRenderEvent) {
        if (overlaysHidden) return

        for (overlay in overlays.values) {
            if (overlay.shouldRender()) {
                try {
                    overlay.render(event.guiGraphics, event.tickDelta)
                } catch (e: Exception) {
                    CivutilsMod.logger.error("Error rendering overlay ${overlay.id}", e)
                }
            }
        }
    }

    /**
     * Render overlay previews (for config GUI).
     */
    fun renderPreviews(guiGraphics: GuiGraphics, tickDelta: Float) {
        for (overlay in overlays.values) {
            try {
                overlay.renderPreview(guiGraphics, tickDelta)
            } catch (e: Exception) {
                CivutilsMod.logger.error("Error rendering overlay preview ${overlay.id}", e)
            }
        }
    }

    /**
     * Get enabled overlays.
     */
    fun getEnabledOverlays(): List<Overlay> {
        return overlays.values.filter { it.enabled.value }
    }

    /**
     * Get disabled overlays.
     */
    fun getDisabledOverlays(): List<Overlay> {
        return overlays.values.filter { !it.enabled.value }
    }

    /**
     * Find overlay at a screen position (for config GUI click detection).
     */
    fun getOverlayAt(x: Int, y: Int): Overlay? {
        // Check in reverse order so topmost overlays are found first
        return overlays.values.reversed().find { overlay ->
            overlay.enabled.value && overlay.containsPoint(x, y)
        }
    }
}
