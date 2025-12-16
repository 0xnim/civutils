package xyz.nim.civutils.gui.widgets

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.SliderWidget
import net.minecraft.text.Text
import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.gui.theme.CivutilsTheme
import kotlin.math.roundToInt

/**
 * Color constants for the UI.
 * These are now aliases to CivutilsTheme for backward compatibility.
 */
object Colors {
    val BACKGROUND = CivutilsTheme.BACKGROUND
    val BACKGROUND_LIGHT = CivutilsTheme.BACKGROUND_LIGHT
    val BACKGROUND_HOVER = CivutilsTheme.BACKGROUND_HOVER
    val ACCENT = CivutilsTheme.ACCENT
    val ACCENT_HOVER = CivutilsTheme.ACCENT_HOVER
    val TEXT = CivutilsTheme.TEXT_PRIMARY
    val TEXT_SECONDARY = CivutilsTheme.TEXT_SECONDARY
    val SUCCESS = CivutilsTheme.SUCCESS
    val ERROR = CivutilsTheme.ERROR
    val WARNING = CivutilsTheme.WARNING
    val ENABLED = CivutilsTheme.ENABLED
    val DISABLED = CivutilsTheme.DISABLED
}

/**
 * A toggle button for boolean configs.
 */
class ToggleButton(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    private val config: Config<Boolean>,
    private val label: String
) : ButtonWidget(
    x, y, width, height,
    Text.literal(if (config.value) "§a$label: ON" else "§c$label: OFF"),
    { button ->
        config.value = !config.value
        button.message = Text.literal(if (config.value) "§a$label: ON" else "§c$label: OFF")
    },
    DEFAULT_NARRATION_SUPPLIER
) {
    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val mc = MinecraftClient.getInstance()
        val color = if (isHovered) Colors.BACKGROUND_HOVER else Colors.BACKGROUND_LIGHT
        context.fill(x, y, x + width, y + height, color)

        // Draw border
        val borderColor = if (config.value) Colors.ENABLED else Colors.DISABLED
        context.drawBorder(x, y, width, height, borderColor)

        // Draw text centered
        val textX = x + width / 2 - mc.textRenderer.getWidth(message) / 2
        val textY = y + (height - 8) / 2
        context.drawText(mc.textRenderer, message, textX, textY, Colors.TEXT, true)
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
    private val config: Config<Int>,
    private val label: String,
    private val min: Int,
    private val max: Int
) : SliderWidget(
    x, y, width, height,
    Text.literal("$label: ${config.value}"),
    ((config.value - min).toDouble() / (max - min).toDouble()).coerceIn(0.0, 1.0)
) {
    override fun updateMessage() {
        message = Text.literal("$label: ${getValue()}")
    }

    override fun applyValue() {
        config.value = getValue()
    }

    private fun getValue(): Int {
        return (min + (value * (max - min))).roundToInt()
    }

    override fun renderWidget(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val mc = MinecraftClient.getInstance()

        // Background
        context.fill(x, y, x + width, y + height, Colors.BACKGROUND_LIGHT)

        // Filled portion
        val filledWidth = (width * value).toInt()
        context.fill(x, y, x + filledWidth, y + height, Colors.ACCENT)

        // Border
        context.drawBorder(x, y, width, height, if (isHovered) Colors.ACCENT_HOVER else Colors.TEXT_SECONDARY)

        // Text
        val textX = x + width / 2 - mc.textRenderer.getWidth(message) / 2
        val textY = y + (height - 8) / 2
        context.drawText(mc.textRenderer, message, textX, textY, Colors.TEXT, true)
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
): ButtonWidget {
    return ButtonWidget.builder(Text.literal(text)) { onClick() }
        .dimensions(x, y, width, height)
        .build()
}

/**
 * Draw a tooltip box.
 */
fun DrawContext.drawTooltip(text: List<String>, x: Int, y: Int) {
    val mc = MinecraftClient.getInstance()
    val textRenderer = mc.textRenderer

    if (text.isEmpty()) return

    val maxWidth = text.maxOf { textRenderer.getWidth(it) }
    val totalHeight = text.size * 10

    val boxX = x + 8
    val boxY = y - 4

    // Background
    fill(boxX - 3, boxY - 3, boxX + maxWidth + 3, boxY + totalHeight + 3, Colors.BACKGROUND)
    drawBorder(boxX - 3, boxY - 3, maxWidth + 6, totalHeight + 6, Colors.TEXT_SECONDARY)

    // Text
    var lineY = boxY
    for (line in text) {
        drawText(textRenderer, line, boxX, lineY, Colors.TEXT, false)
        lineY += 10
    }
}
