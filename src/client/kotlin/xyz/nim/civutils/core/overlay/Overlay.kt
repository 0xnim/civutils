package xyz.nim.civutils.core.overlay

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.config.Persisted

/**
 * Base class for all HUD overlays.
 *
 * Overlays are positioned using the anchor system - the screen is divided into
 * a 3x3 grid and overlays are positioned relative to one of these sections.
 *
 * Usage:
 * ```
 * class MyOverlay : Overlay(
 *     position = OverlayPosition.topLeft(),
 *     size = OverlaySize(100, 20)
 * ) {
 *     override fun render(context: DrawContext, tickDelta: Float) {
 *         // render your overlay
 *     }
 * }
 * ```
 */
abstract class Overlay(
    /**
     * The position of this overlay on screen.
     */
    val position: OverlayPosition,

    /**
     * The size of this overlay.
     */
    val size: OverlaySize
) {
    /**
     * Unique identifier for this overlay. Defaults to class simple name.
     */
    open val id: String get() = this::class.simpleName ?: "UnknownOverlay"

    /**
     * Display name for the config GUI.
     */
    open val displayName: String get() = id.replace(Regex("([A-Z])"), " $1").trim()

    /**
     * Whether this overlay is currently enabled.
     */
    @Persisted
    val enabled = Config(defaultValue = true)

    /**
     * Whether this overlay should render.
     * Can be overridden for conditional visibility.
     */
    open fun shouldRender(): Boolean {
        if (!enabled.value) return false
        if (!CivutilsMod.isInGame()) return false
        return true
    }

    /**
     * Called every tick to update overlay state.
     * Override for per-tick logic.
     */
    open fun tick() {}

    /**
     * Render this overlay.
     *
     * @param context The draw context for rendering
     * @param tickDelta Partial tick for smooth animations
     */
    abstract fun render(context: DrawContext, tickDelta: Float)

    /**
     * Render a preview of this overlay (for config GUI).
     * By default, calls render().
     */
    open fun renderPreview(context: DrawContext, tickDelta: Float) {
        render(context, tickDelta)
    }

    /**
     * Called when a config for this overlay changes.
     */
    open fun onConfigUpdate(config: Config<*>) {}

    /**
     * Get the actual X position to render at.
     */
    fun getRenderX(): Int {
        val mc = MinecraftClient.getInstance()
        val window = mc.window
        return position.getRenderX(window.scaledWidth, size.width)
    }

    /**
     * Get the actual Y position to render at.
     */
    fun getRenderY(): Int {
        val mc = MinecraftClient.getInstance()
        val window = mc.window
        return position.getRenderY(window.scaledHeight, size.height)
    }

    /**
     * Check if a point is within this overlay's bounds.
     * Useful for click detection in config GUI.
     */
    fun containsPoint(x: Int, y: Int): Boolean {
        val renderX = getRenderX()
        val renderY = getRenderY()
        return x >= renderX && x <= renderX + size.width &&
               y >= renderY && y <= renderY + size.height
    }

    /**
     * Get all configs for this overlay.
     */
    fun getConfigs(): List<Config<*>> {
        return CivutilsMod.configManager.getConfigsForOwner(this)
    }

    override fun toString(): String = "Overlay($id, enabled=${enabled.value})"
}
