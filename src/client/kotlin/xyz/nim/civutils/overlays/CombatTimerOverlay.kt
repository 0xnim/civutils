package xyz.nim.civutils.overlays

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.booleanConfig
import xyz.nim.civutils.core.config.colorConfig
import xyz.nim.civutils.core.config.intConfig
import xyz.nim.civutils.core.config.onChange
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.core.overlay.Overlay
import xyz.nim.civutils.core.overlay.OverlayPosition
import xyz.nim.civutils.core.overlay.OverlaySize
import xyz.nim.civutils.models.BossBarModel
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.BooleanConfig
import xyz.nim.lib.config.options.ColorConfig
import xyz.nim.lib.config.options.IntegerConfig

/**
 * CombatTimerOverlay: Shows combat log timer countdown.
 *
 * Displays a countdown when the player is in combat, indicating
 * time remaining before safe logout. Parses the server's Combat-Log Timer BossBar.
 */
class CombatTimerOverlay : Overlay(
    position = OverlayPosition.topCenter(offsetX = 0, offsetY = 80),
    size = OverlaySize(width = 200, height = 40)
) {
    override val displayName = "Combat Timer"
    override val requiredFeature = "combatTimer"

    private val mc: Minecraft get() = Minecraft.getInstance()

    // === Display Options ===

    /**
     * Show progress bar under the timer.
     */
    val showProgressBar: BooleanConfig = booleanConfig(
        name = "showProgressBar",
        default = true,
        displayName = "Show Progress Bar",
        comment = "Show a progress bar under the countdown"
    ).onChange { onConfigUpdate(showProgressBar) }

    /**
     * Show decimal seconds (e.g., "5.2s" instead of "5s").
     */
    val showDecimalSeconds: BooleanConfig = booleanConfig(
        name = "showDecimalSeconds",
        default = true,
        displayName = "Show Decimal",
        comment = "Show decimal seconds in countdown"
    ).onChange { onConfigUpdate(showDecimalSeconds) }

    /**
     * Play warning sound when timer is low.
     */
    val playWarningSound: BooleanConfig = booleanConfig(
        name = "playWarningSound",
        default = false,
        displayName = "Warning Sound",
        comment = "Play a sound when timer is below 5 seconds"
    ).onChange { onConfigUpdate(playWarningSound) }

    /**
     * Hide the vanilla BossBar when combat timer is active.
     * NOTE: Currently disabled - causes issues with server connection.
     */
    val hideVanillaBossBar: BooleanConfig = booleanConfig(
        name = "hideVanillaBossBar",
        default = false,
        displayName = "Hide Vanilla Bar",
        comment = "Hide the vanilla combat timer BossBar (not yet working)"
    ).onChange {
        // Feature disabled for now - causes network protocol errors
        // BossBarModel.hideCombatTimerBar = hideVanillaBossBar.value
        onConfigUpdate(hideVanillaBossBar)
    }

    // === Bar Style Options ===

    /**
     * Progress bar width in pixels.
     */
    val barWidth: IntegerConfig = intConfig(
        name = "barWidth",
        default = 180,
        min = 80,
        max = 300,
        displayName = "Bar Width",
        comment = "Width of the progress bar"
    ).onChange { onConfigUpdate(barWidth) }

    /**
     * Progress bar height in pixels.
     */
    val barHeight: IntegerConfig = intConfig(
        name = "barHeight",
        default = 8,
        min = 4,
        max = 16,
        displayName = "Bar Height",
        comment = "Height of the progress bar"
    ).onChange { onConfigUpdate(barHeight) }

    /**
     * Progress bar background color.
     */
    val barBackgroundColor: ColorConfig = colorConfig(
        name = "barBackgroundColor",
        default = 0xFF333333.toInt(),
        displayName = "Bar Background",
        comment = "Progress bar background color"
    ).onChange { onConfigUpdate(barBackgroundColor) }

    /**
     * Progress bar fill color (changes to warning color when low).
     */
    val barFillColor: ColorConfig = colorConfig(
        name = "barFillColor",
        default = 0xFFFF5555.toInt(),
        displayName = "Bar Fill",
        comment = "Progress bar fill color"
    ).onChange { onConfigUpdate(barFillColor) }

    /**
     * Progress bar fill color when timer is critically low (<5s).
     */
    val barWarningColor: ColorConfig = colorConfig(
        name = "barWarningColor",
        default = 0xFFFFAA00.toInt(),
        displayName = "Warning Color",
        comment = "Bar color when timer is below 5 seconds"
    ).onChange { onConfigUpdate(barWarningColor) }

    /**
     * Text color.
     */
    val textColor: ColorConfig = colorConfig(
        name = "textColor",
        default = 0xFFFFFFFF.toInt(),
        displayName = "Text Color",
        comment = "Countdown text color"
    ).onChange { onConfigUpdate(textColor) }

    override fun getConfigs(): List<ConfigOption<*>> = listOf(
        enabled, showProgressBar, showDecimalSeconds, playWarningSound, hideVanillaBossBar,
        barWidth, barHeight, barBackgroundColor, barFillColor, barWarningColor, textColor
    )

    override fun onConfigUpdate(config: ConfigOption<*>) {
        CivutilsMod.configManager.markDirty()
    }

    override fun shouldRender(): Boolean {
        if (!super.shouldRender()) return false
        return BossBarModel.isCombatTimerActive()
    }

    override fun render(guiGraphics: GuiGraphics, tickDelta: Float) {
        if (!BossBarModel.isCombatTimerActive()) return

        val font = mc.font
        val x = getRenderX()
        var y = getRenderY()

        val progress = BossBarModel.combatTimer.progress
        val secondsRemaining = BossBarModel.getCombatTimerSecondsRemaining() ?: return

        // Determine if we're in warning territory (<5 seconds)
        val isWarning = secondsRemaining < 5f

        // Format countdown text
        val countdownText = if (showDecimalSeconds.value) {
            String.format("%.1fs", secondsRemaining)
        } else {
            "${secondsRemaining.toInt()}s"
        }

        // Title + countdown
        val titleText = "COMBAT"
        val fullText = "$titleText $countdownText"

        // Center the text
        val textWidth = font.width(fullText)
        val textX = x + (barWidth.value - textWidth) / 2

        // Draw title in red, countdown in white/warning color
        val titleWidth = font.width("$titleText ")
        guiGraphics.drawString(font, titleText, textX, y, 0xFFFF5555.toInt(), true)
        val countdownColor = if (isWarning) barWarningColor.value else textColor.value
        guiGraphics.drawString(font, countdownText, textX + titleWidth, y, countdownColor, true)

        y += font.lineHeight + 4

        // Draw progress bar if enabled
        if (showProgressBar.value) {
            val fillColor = if (isWarning) barWarningColor.value else barFillColor.value
            drawProgressBar(guiGraphics, x, y, barWidth.value, barHeight.value, progress, fillColor)
        }

        // Update overlay size
        size.width = barWidth.value
        size.height = font.lineHeight + 4 + (if (showProgressBar.value) barHeight.value else 0)
    }

    override fun renderPreview(guiGraphics: GuiGraphics, tickDelta: Float) {
        val font = mc.font
        val x = getRenderX()
        var y = getRenderY()

        // Preview with sample data
        val countdownText = if (showDecimalSeconds.value) "12.5s" else "12s"
        val titleText = "COMBAT"
        val fullText = "$titleText $countdownText"

        val textWidth = font.width(fullText)
        val textX = x + (barWidth.value - textWidth) / 2

        val titleWidth = font.width("$titleText ")
        guiGraphics.drawString(font, titleText, textX, y, 0xFFFF5555.toInt(), true)
        guiGraphics.drawString(font, countdownText, textX + titleWidth, y, textColor.value, true)

        y += font.lineHeight + 4

        if (showProgressBar.value) {
            drawProgressBar(guiGraphics, x, y, barWidth.value, barHeight.value, 0.42f, barFillColor.value)
        }

        // Update preview size
        size.width = barWidth.value
        size.height = font.lineHeight + 4 + (if (showProgressBar.value) barHeight.value else 0)
    }

    private fun drawProgressBar(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        progress: Float,
        fillColor: Int
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)

        // Background
        guiGraphics.fill(x, y, x + width, y + height, barBackgroundColor.value)

        // Fill (progress from right to left for countdown effect)
        val fillWidth = (width * clampedProgress).toInt()
        if (fillWidth > 0) {
            guiGraphics.fill(x, y, x + fillWidth, y + height, fillColor)
        }

        // Border
        guiGraphics.fill(x, y, x + width, y + 1, 0xFF000000.toInt())
        guiGraphics.fill(x, y + height - 1, x + width, y + height, 0xFF000000.toInt())
        guiGraphics.fill(x, y, x + 1, y + height, 0xFF000000.toInt())
        guiGraphics.fill(x + width - 1, y, x + width, y + height, 0xFF000000.toInt())
    }
}
