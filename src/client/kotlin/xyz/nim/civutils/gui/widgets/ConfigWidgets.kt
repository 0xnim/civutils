package xyz.nim.civutils.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.AbstractSliderButton
import net.minecraft.network.chat.Component
import xyz.nim.civutils.core.config.value
import xyz.nim.lib.config.options.BooleanConfig
import xyz.nim.lib.config.options.IntegerConfig
import xyz.nim.lib.ui.NlibTheme
import kotlin.math.roundToInt
import xyz.nim.civutils.utils.renderOutline

/**
 * Color constants for the UI.
 * These are now aliases to NlibTheme for backward compatibility.
 */
object Colors {
    val BACKGROUND = NlibTheme.BACKGROUND
    val BACKGROUND_LIGHT = NlibTheme.BACKGROUND_LIGHT
    val BACKGROUND_HOVER = NlibTheme.BACKGROUND_HOVER
    val ACCENT = NlibTheme.ACCENT
    val ACCENT_HOVER = NlibTheme.ACCENT_HOVER
    val TEXT = NlibTheme.TEXT_PRIMARY
    val TEXT_SECONDARY = NlibTheme.TEXT_SECONDARY
    val SUCCESS = NlibTheme.SUCCESS
    val ERROR = NlibTheme.ERROR
    val WARNING = NlibTheme.WARNING
    val ENABLED = NlibTheme.ENABLED
    val DISABLED = NlibTheme.DISABLED
}

/**
 * A toggle button for boolean configs.
 */
class ToggleButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val config: BooleanConfig,
    private val label: String
) : Button(
    x, y, width, height,
    Component.literal(if (config.value) "§a$label: ON" else "§c$label: OFF"),
    { button ->
        config.value = !config.value
        button.message = Component.literal(if (config.value) "§a$label: ON" else "§c$label: OFF")
    },
    DEFAULT_NARRATION
) {
    override fun renderContents(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val mc = Minecraft.getInstance()
        // Draw text centered
        val textX = x + width / 2 - mc.font.width(message) / 2
        val textY = y + (height - 8) / 2
        guiGraphics.drawString(mc.font, message, textX, textY, Colors.TEXT, true)
    }
}

/**
 * A slider for integer configs.
 */
class IntSlider(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val config: IntegerConfig,
    private val label: String
) : AbstractSliderButton(
    x, y, width, height,
    Component.literal("$label: ${config.value}"),
    ((config.value - config.minValue).toDouble() / (config.maxValue - config.minValue).toDouble()).coerceIn(0.0, 1.0)
) {
    private val min: Int = config.minValue
    private val max: Int = config.maxValue

    override fun updateMessage() {
        message = Component.literal("$label: ${getValue()}")
    }

    override fun applyValue() {
        config.value = getValue()
    }

    private fun getValue(): Int {
        return (min + (value * (max - min))).roundToInt()
    }

    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        val mc = Minecraft.getInstance()

        // Background
        guiGraphics.fill(x, y, x + width, y + height, Colors.BACKGROUND_LIGHT)

        // Filled portion
        val filledWidth = (width * value).toInt()
        guiGraphics.fill(x, y, x + filledWidth, y + height, Colors.ACCENT)

        // Border
        guiGraphics.renderOutline(x, y, width, height, if (isHovered) Colors.ACCENT_HOVER else Colors.TEXT_SECONDARY)

        // Text
        val textX = x + width / 2 - mc.font.width(message) / 2
        val textY = y + (height - 8) / 2
        guiGraphics.drawString(mc.font, message, textX, textY, Colors.TEXT, true)
    }
}

/**
 * A simple text button.
 */
fun textButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    text: String,
    onClick: () -> Unit
): Button {
    return Button.builder(Component.literal(text)) { onClick() }
        .bounds(x, y, width, height)
        .build()
}

/**
 * Draw a tooltip box.
 */
fun GuiGraphics.drawTooltip(text: List<String>, x: Int, y: Int) {
    val mc = Minecraft.getInstance()
    val font = mc.font

    if (text.isEmpty()) return

    val maxWidth = text.maxOf { font.width(it) }
    val totalHeight = text.size * 10

    val boxX = x + 8
    val boxY = y - 4

    // Background
    fill(boxX - 3, boxY - 3, boxX + maxWidth + 3, boxY + totalHeight + 3, Colors.BACKGROUND)
    renderOutline(boxX - 3, boxY - 3, maxWidth + 6, totalHeight + 6, Colors.TEXT_SECONDARY)

    // Text
    var lineY = boxY
    for (line in text) {
        drawString(font, line, boxX, lineY, Colors.TEXT, false)
        lineY += 10
    }
}
