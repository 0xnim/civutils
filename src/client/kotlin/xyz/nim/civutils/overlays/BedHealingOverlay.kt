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
 * BedHealingOverlay: Shows bed healing progress.
 *
 * Displays healing progress when the player is resting in a bed.
 * Renders above the sleep darkening effect for visibility.
 */
class BedHealingOverlay : Overlay(
    position = OverlayPosition.center(offsetX = 0, offsetY = 50),
    size = OverlaySize(width = 200, height = 30)
) {
    override val displayName = "Bed Healing"
    override val requiredFeature = "bedHealing"

    private val mc: Minecraft get() = Minecraft.getInstance()

    // === Display Options ===

    /**
     * Show percentage text.
     */
    val showPercentage: BooleanConfig = booleanConfig(
        name = "showPercentage",
        default = true,
        displayName = "Show Percentage",
        comment = "Show healing percentage"
    ).onChange { onConfigUpdate(showPercentage) }

    /**
     * Show progress bar.
     */
    val showProgressBar: BooleanConfig = booleanConfig(
        name = "showProgressBar",
        default = true,
        displayName = "Show Progress Bar",
        comment = "Show a progress bar for healing"
    ).onChange { onConfigUpdate(showProgressBar) }

    /**
     * Show "Healing..." title.
     */
    val showTitle: BooleanConfig = booleanConfig(
        name = "showTitle",
        default = true,
        displayName = "Show Title",
        comment = "Show 'Healing...' title"
    ).onChange { onConfigUpdate(showTitle) }

    /**
     * Show estimated time remaining.
     */
    val showTimeEstimate: BooleanConfig = booleanConfig(
        name = "showTimeEstimate",
        default = true,
        displayName = "Show Time Estimate",
        comment = "Show estimated time remaining to full health"
    ).onChange { onConfigUpdate(showTimeEstimate) }

    /**
     * Hide the vanilla BossBar when healing.
     * NOTE: Currently disabled - causes issues with server connection.
     */
    val hideVanillaBossBar: BooleanConfig = booleanConfig(
        name = "hideVanillaBossBar",
        default = false,
        displayName = "Hide Vanilla Bar",
        comment = "Hide the vanilla healing BossBar (not yet working)"
    ).onChange {
        // Feature disabled for now - causes network protocol errors
        // BossBarModel.hideBedHealingBar = hideVanillaBossBar.value
        onConfigUpdate(hideVanillaBossBar)
    }

    // === Bar Style Options ===

    /**
     * Progress bar width in pixels.
     */
    val barWidth: IntegerConfig = intConfig(
        name = "barWidth",
        default = 160,
        min = 60,
        max = 300,
        displayName = "Bar Width",
        comment = "Width of the progress bar"
    ).onChange { onConfigUpdate(barWidth) }

    /**
     * Progress bar height in pixels.
     */
    val barHeight: IntegerConfig = intConfig(
        name = "barHeight",
        default = 10,
        min = 4,
        max = 20,
        displayName = "Bar Height",
        comment = "Height of the progress bar"
    ).onChange { onConfigUpdate(barHeight) }

    /**
     * Progress bar background color.
     */
    val barBackgroundColor: ColorConfig = colorConfig(
        name = "barBackgroundColor",
        default = 0xFF222222.toInt(),
        displayName = "Bar Background",
        comment = "Progress bar background color"
    ).onChange { onConfigUpdate(barBackgroundColor) }

    /**
     * Progress bar fill color.
     */
    val barFillColor: ColorConfig = colorConfig(
        name = "barFillColor",
        default = 0xFF55FF55.toInt(),
        displayName = "Bar Fill",
        comment = "Progress bar fill color"
    ).onChange { onConfigUpdate(barFillColor) }

    /**
     * Progress bar border color.
     */
    val barBorderColor: ColorConfig = colorConfig(
        name = "barBorderColor",
        default = 0xFF000000.toInt(),
        displayName = "Bar Border",
        comment = "Progress bar border color"
    ).onChange { onConfigUpdate(barBorderColor) }

    /**
     * Text color.
     */
    val textColor: ColorConfig = colorConfig(
        name = "textColor",
        default = 0xFFFFFFFF.toInt(),
        displayName = "Text Color",
        comment = "Text color for labels"
    ).onChange { onConfigUpdate(textColor) }

    /**
     * Title color.
     */
    val titleColor: ColorConfig = colorConfig(
        name = "titleColor",
        default = 0xFF55FF55.toInt(),
        displayName = "Title Color",
        comment = "Title 'Healing...' color"
    ).onChange { onConfigUpdate(titleColor) }

    override fun getConfigs(): List<ConfigOption<*>> = listOf(
        enabled, showPercentage, showProgressBar, showTitle, showTimeEstimate, hideVanillaBossBar,
        barWidth, barHeight, barBackgroundColor, barFillColor, barBorderColor,
        textColor, titleColor
    )

    override fun onConfigUpdate(config: ConfigOption<*>) {
        CivutilsMod.configManager.markDirty()
    }

    override fun shouldRender(): Boolean {
        if (!super.shouldRender()) return false
        return BossBarModel.isBedHealingActive()
    }

    override fun render(guiGraphics: GuiGraphics, tickDelta: Float) {
        if (!BossBarModel.isBedHealingActive()) return

        val font = mc.font
        var x = getRenderX()
        var y = getRenderY()

        val progress = BossBarModel.bedHealing.progress
        val percent = (progress * 100).toInt()
        val secondsRemaining = BossBarModel.getBedHealingSecondsRemaining()

        // Calculate total height for positioning
        var totalHeight = 0
        if (showTitle.value) totalHeight += font.lineHeight + 4
        if (showProgressBar.value) totalHeight += barHeight.value + 4
        if (showPercentage.value && !showTitle.value && !showProgressBar.value) {
            totalHeight += font.lineHeight
        }
        if (showTimeEstimate.value) totalHeight += font.lineHeight + 2

        // Draw title
        if (showTitle.value) {
            val titleText = "Healing..."
            val titleWidth = font.width(titleText)
            val titleX = x + (barWidth.value - titleWidth) / 2
            guiGraphics.drawString(font, titleText, titleX, y, titleColor.value, true)
            y += font.lineHeight + 4
        }

        // Draw progress bar
        if (showProgressBar.value) {
            drawProgressBar(guiGraphics, x, y, barWidth.value, barHeight.value, progress)

            // Draw percentage inline with bar if enabled
            if (showPercentage.value) {
                val percentText = "$percent%"
                val percentWidth = font.width(percentText)
                val percentX = x + (barWidth.value - percentWidth) / 2
                val percentY = y + (barHeight.value - font.lineHeight) / 2
                guiGraphics.drawString(font, percentText, percentX, percentY, textColor.value, true)
            }
            y += barHeight.value + 4
        } else if (showPercentage.value) {
            // Show percentage standalone if no bar
            val percentText = "$percent%"
            val percentWidth = font.width(percentText)
            val percentX = x + (barWidth.value - percentWidth) / 2
            guiGraphics.drawString(font, percentText, percentX, y, textColor.value, true)
            y += font.lineHeight + 4
        }

        // Draw time estimate
        if (showTimeEstimate.value) {
            val timeText = if (secondsRemaining != null && secondsRemaining > 0) {
                val secs = secondsRemaining.toInt()
                if (secs >= 60) {
                    val mins = secs / 60
                    val remainingSecs = secs % 60
                    "~${mins}m ${remainingSecs}s remaining"
                } else {
                    "~${secs}s remaining"
                }
            } else {
                "Calculating..."
            }
            val timeWidth = font.width(timeText)
            val timeX = x + (barWidth.value - timeWidth) / 2
            guiGraphics.drawString(font, timeText, timeX, y, 0xFFAAAAAA.toInt(), true)
        }

        // Update overlay size
        size.width = barWidth.value
        size.height = totalHeight
    }

    override fun renderPreview(guiGraphics: GuiGraphics, tickDelta: Float) {
        val font = mc.font
        var x = getRenderX()
        var y = getRenderY()

        // Preview with sample data
        val progress = 0.65f
        val percent = 65

        var totalHeight = 0
        if (showTitle.value) totalHeight += font.lineHeight + 4
        if (showProgressBar.value) totalHeight += barHeight.value + 4
        if (showPercentage.value && !showTitle.value && !showProgressBar.value) {
            totalHeight += font.lineHeight
        }
        if (showTimeEstimate.value) totalHeight += font.lineHeight + 2

        // Draw title
        if (showTitle.value) {
            val titleText = "Healing..."
            val titleWidth = font.width(titleText)
            val titleX = x + (barWidth.value - titleWidth) / 2
            guiGraphics.drawString(font, titleText, titleX, y, titleColor.value, true)
            y += font.lineHeight + 4
        }

        // Draw progress bar
        if (showProgressBar.value) {
            drawProgressBar(guiGraphics, x, y, barWidth.value, barHeight.value, progress)

            if (showPercentage.value) {
                val percentText = "$percent%"
                val percentWidth = font.width(percentText)
                val percentX = x + (barWidth.value - percentWidth) / 2
                val percentY = y + (barHeight.value - font.lineHeight) / 2
                guiGraphics.drawString(font, percentText, percentX, percentY, textColor.value, true)
            }
            y += barHeight.value + 4
        } else if (showPercentage.value) {
            val percentText = "$percent%"
            val percentWidth = font.width(percentText)
            val percentX = x + (barWidth.value - percentWidth) / 2
            guiGraphics.drawString(font, percentText, percentX, y, textColor.value, true)
            y += font.lineHeight + 4
        }

        // Draw time estimate preview
        if (showTimeEstimate.value) {
            val timeText = "~23s remaining"
            val timeWidth = font.width(timeText)
            val timeX = x + (barWidth.value - timeWidth) / 2
            guiGraphics.drawString(font, timeText, timeX, y, 0xFFAAAAAA.toInt(), true)
        }

        // Update preview size
        size.width = barWidth.value
        size.height = totalHeight
    }

    private fun drawProgressBar(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        progress: Float
    ) {
        val clampedProgress = progress.coerceIn(0f, 1f)

        // Background (solid, no transparency issues)
        guiGraphics.fill(x, y, x + width, y + height, barBackgroundColor.value)

        // Fill
        val fillWidth = (width * clampedProgress).toInt()
        if (fillWidth > 0) {
            guiGraphics.fill(x, y, x + fillWidth, y + height, barFillColor.value)
        }

        // Border
        guiGraphics.fill(x, y, x + width, y + 1, barBorderColor.value)
        guiGraphics.fill(x, y + height - 1, x + width, y + height, barBorderColor.value)
        guiGraphics.fill(x, y, x + 1, y + height, barBorderColor.value)
        guiGraphics.fill(x + width - 1, y, x + width, y + height, barBorderColor.value)
    }
}
