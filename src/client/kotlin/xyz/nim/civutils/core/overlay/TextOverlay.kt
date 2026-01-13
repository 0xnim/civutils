package xyz.nim.civutils.core.overlay

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.colorConfig
import xyz.nim.civutils.core.config.enumConfig
import xyz.nim.civutils.core.config.onChange
import xyz.nim.civutils.core.config.value
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.ColorConfig
import xyz.nim.lib.config.options.OptionListConfig

/**
 * Text shadow/outline style options.
 */
enum class TextShadow {
    /** No shadow */
    NONE,
    /** Standard Minecraft drop shadow */
    SHADOW,
    /** Full outline around text (more visible) */
    OUTLINE
}

/**
 * An overlay that renders text with optional formatting.
 *
 * Supports a simple template system where text can include placeholders
 * that are resolved at render time.
 *
 * Usage:
 * ```
 * class MyTextOverlay : TextOverlay(
 *     position = OverlayPosition.topLeft(),
 *     size = OverlaySize(150, 20)
 * ) {
 *     override fun getTemplate(): String {
 *         val health = mc.player?.health?.toInt() ?: 0
 *         return "§cHealth: §f$health"
 *     }
 * }
 * ```
 */
abstract class TextOverlay(
    position: OverlayPosition,
    size: OverlaySize
) : Overlay(position, size) {

    protected val mc: Minecraft get() = Minecraft.getInstance()

    /**
     * Text shadow style.
     */
    val textShadow: OptionListConfig<TextShadow> = enumConfig(
        name = "textShadow",
        default = TextShadow.SHADOW,
        displayName = "Text Shadow",
        comment = "Text shadow style"
    ).onChange { onConfigUpdate(textShadow) }

    /**
     * Text color (ARGB format). Use -1 for white.
     */
    val textColor: ColorConfig = colorConfig(
        name = "textColor",
        default = 0xFFFFFFFF.toInt(),
        includeAlpha = true,
        displayName = "Text Color",
        comment = "Text color"
    ).onChange { onConfigUpdate(textColor) }

    /**
     * Get configs including base overlay configs.
     */
    override fun getConfigs(): List<ConfigOption<*>> = listOf(
        enabled, textShadow, textColor
    )

    /**
     * Cached rendered lines from the template.
     */
    private var cachedLines: List<String> = emptyList()

    /**
     * Get the text template to render.
     * Can include Minecraft formatting codes (§) and placeholders.
     *
     * Return empty string to hide the overlay.
     */
    protected abstract fun getTemplate(): String

    /**
     * Get a preview template for the config GUI.
     * Override to provide sample data.
     */
    protected open fun getPreviewTemplate(): String = getTemplate()

    override fun onConfigUpdate(config: ConfigOption<*>) {
        CivutilsMod.configManager.markDirty()
    }

    /**
     * Update the cached text. Called every tick.
     */
    override fun tick() {
        super.tick()
        val template = getTemplate()
        cachedLines = if (template.isEmpty()) {
            emptyList()
        } else {
            template.split("\n")
        }
    }

    /**
     * Check if the overlay should render.
     * Returns false if the template is empty.
     */
    override fun shouldRender(): Boolean {
        if (!super.shouldRender()) return false
        return cachedLines.isNotEmpty()
    }

    override fun render(guiGraphics: GuiGraphics, tickDelta: Float) {
        if (cachedLines.isEmpty()) return

        val font = mc.font
        val x = getRenderX()
        var y = getRenderY()

        val lineHeight = font.lineHeight + 1

        for (line in cachedLines) {
            drawText(guiGraphics, line, x, y)
            y += lineHeight
        }
    }

    override fun renderPreview(guiGraphics: GuiGraphics, tickDelta: Float) {
        val previewLines = getPreviewTemplate().split("\n")
        if (previewLines.isEmpty()) return

        val font = mc.font
        val x = getRenderX()
        var y = getRenderY()

        val lineHeight = font.lineHeight + 1

        for (line in previewLines) {
            drawText(guiGraphics, line, x, y)
            y += lineHeight
        }
    }

    /**
     * Draw a single line of text with the configured shadow style.
     */
    private fun drawText(guiGraphics: GuiGraphics, text: String, x: Int, y: Int) {
        val font = mc.font
        val color = textColor.value

        when (textShadow.value) {
            TextShadow.NONE -> {
                guiGraphics.drawString(font, text, x, y, color, false)
            }
            TextShadow.SHADOW -> {
                guiGraphics.drawString(font, text, x, y, color, true)
            }
            TextShadow.OUTLINE -> {
                // Draw outline by rendering text in 4 directions
                val shadowColor = 0xFF000000.toInt()
                guiGraphics.drawString(font, text, x - 1, y, shadowColor, false)
                guiGraphics.drawString(font, text, x + 1, y, shadowColor, false)
                guiGraphics.drawString(font, text, x, y - 1, shadowColor, false)
                guiGraphics.drawString(font, text, x, y + 1, shadowColor, false)
                // Draw main text on top
                guiGraphics.drawString(font, text, x, y, color, false)
            }
        }
    }

    /**
     * Calculate the width of the template text.
     */
    protected fun getTextWidth(text: String): Int {
        return mc.font.width(text)
    }

    /**
     * Calculate the height of the template text.
     */
    protected fun getTextHeight(): Int {
        val lineCount = cachedLines.size.coerceAtLeast(1)
        return (mc.font.lineHeight + 1) * lineCount
    }
}
