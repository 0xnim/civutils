package xyz.nim.civutils.core.overlay

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.booleanConfig
import xyz.nim.civutils.core.config.value
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.BooleanConfig

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
 *     val myConfig = intConfig("myConfig", 100, min = 0, max = 200)
 *
 *     override fun getConfigs() = listOf(super.getConfigs(), myConfig).flatten()
 *
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
    val enabled: BooleanConfig = booleanConfig(
        name = "enabled",
        default = true,
        displayName = "Enabled",
        comment = "Whether this overlay is shown"
    ).also { config ->
        config.onValueChanged { newValue ->
            onConfigUpdate(config)
            CivutilsMod.configManager.markDirty()
        }
    }

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
     * @param guiGraphics The graphics context for rendering
     * @param tickDelta Partial tick for smooth animations
     */
    abstract fun render(guiGraphics: GuiGraphics, tickDelta: Float)

    /**
     * Render a preview of this overlay (for config GUI).
     * By default, calls render().
     */
    open fun renderPreview(guiGraphics: GuiGraphics, tickDelta: Float) {
        render(guiGraphics, tickDelta)
    }

    /**
     * Called when a config for this overlay changes.
     */
    open fun onConfigUpdate(config: ConfigOption<*>) {}

    /**
     * Get the actual X position to render at.
     */
    fun getRenderX(): Int {
        val mc = Minecraft.getInstance()
        val window = mc.window
        return position.getRenderX(window.guiScaledWidth, size.width)
    }

    /**
     * Get the actual Y position to render at.
     */
    fun getRenderY(): Int {
        val mc = Minecraft.getInstance()
        val window = mc.window
        return position.getRenderY(window.guiScaledHeight, size.height)
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
     * Override to include overlay-specific configs.
     */
    open fun getConfigs(): List<ConfigOption<*>> = listOf(enabled)

    /**
     * Register this overlay's configs with the config manager.
     * Called by OverlayManager after the overlay is constructed.
     */
    internal fun registerConfigs() {
        val configs = getConfigs()
        if (configs.isNotEmpty()) {
            CivutilsMod.configManager.registerOwner(id, configs)
        }
    }

    override fun toString(): String = "Overlay($id, enabled=${enabled.value})"
}
