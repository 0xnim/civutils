package xyz.nim.civutils.core.overlay

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.config.Persisted

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

    protected val mc: MinecraftClient get() = MinecraftClient.getInstance()

    /**
     * Text shadow style.
     */
    @Persisted
    val textShadow = Config(defaultValue = TextShadow.SHADOW)

    /**
     * Text color (ARGB format). Use -1 for white.
     */
    @Persisted
    val textColor = Config(defaultValue = 0xFFFFFFFF.toInt())

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

    override fun render(context: DrawContext, tickDelta: Float) {
        if (cachedLines.isEmpty()) return

        val textRenderer = mc.textRenderer
        val x = getRenderX()
        var y = getRenderY()

        val lineHeight = textRenderer.fontHeight + 1

        for (line in cachedLines) {
            drawText(context, line, x, y)
            y += lineHeight
        }
    }

    override fun renderPreview(context: DrawContext, tickDelta: Float) {
        val previewLines = getPreviewTemplate().split("\n")
        if (previewLines.isEmpty()) return

        val textRenderer = mc.textRenderer
        val x = getRenderX()
        var y = getRenderY()

        val lineHeight = textRenderer.fontHeight + 1

        for (line in previewLines) {
            drawText(context, line, x, y)
            y += lineHeight
        }
    }

    /**
     * Draw a single line of text with the configured shadow style.
     */
    private fun drawText(context: DrawContext, text: String, x: Int, y: Int) {
        val textRenderer = mc.textRenderer
        val color = textColor.value

        when (textShadow.value) {
            TextShadow.NONE -> {
                context.drawText(textRenderer, text, x, y, color, false)
            }
            TextShadow.SHADOW -> {
                context.drawText(textRenderer, text, x, y, color, true)
            }
            TextShadow.OUTLINE -> {
                // Draw outline by rendering text in 4 directions
                val shadowColor = 0xFF000000.toInt()
                context.drawText(textRenderer, text, x - 1, y, shadowColor, false)
                context.drawText(textRenderer, text, x + 1, y, shadowColor, false)
                context.drawText(textRenderer, text, x, y - 1, shadowColor, false)
                context.drawText(textRenderer, text, x, y + 1, shadowColor, false)
                // Draw main text on top
                context.drawText(textRenderer, text, x, y, color, false)
            }
        }
    }

    /**
     * Calculate the width of the template text.
     */
    protected fun getTextWidth(text: String): Int {
        return mc.textRenderer.getWidth(text)
    }

    /**
     * Calculate the height of the template text.
     */
    protected fun getTextHeight(): Int {
        val lineCount = cachedLines.size.coerceAtLeast(1)
        return (mc.textRenderer.fontHeight + 1) * lineCount
    }
}
