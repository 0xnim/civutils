package xyz.nim.civutils.overlays

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.booleanConfig
import xyz.nim.civutils.core.config.colorConfig
import xyz.nim.civutils.core.config.enumConfig
import xyz.nim.civutils.core.config.intConfig
import xyz.nim.civutils.core.config.onChange
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.core.overlay.Overlay
import xyz.nim.civutils.core.overlay.OverlayPosition
import xyz.nim.civutils.core.overlay.OverlaySize
import xyz.nim.civutils.models.ClassInfo
import xyz.nim.civutils.models.ClassModel
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.BooleanConfig
import xyz.nim.lib.config.options.ColorConfig
import xyz.nim.lib.config.options.IntegerConfig
import xyz.nim.lib.config.options.OptionListConfig

/**
 * Display style for the ClassXP overlay.
 */
enum class DisplayStyle {
    BAR,
    TEXT
}

/**
 * How to calculate XP percentage.
 */
enum class PercentageMode {
    /** Progress within current level toward next level (uses server XP formula) */
    LEVEL_PROGRESS,
    /** Overall progress from 0 XP toward max level (totalXp / maxXp) */
    TOTAL_PROGRESS,
    /** Tier progress - factors in % of total XP in this class (other classes can bring it down) */
    TIER_PROGRESS
}

/**
 * ClassXpOverlay: Shows class XP progress.
 *
 * Data source: /class menu (detailed XP breakdown)
 */
class ClassXpOverlay : Overlay(
    position = OverlayPosition.topRight(offsetX = -5, offsetY = 50),
    size = OverlaySize(width = 160, height = 60)
) {
    override val displayName = "Class XP"
    override val requiredFeature = "classes"

    private val mc: Minecraft get() = Minecraft.getInstance()

    // === Display Options ===

    /**
     * Display style: BAR shows progress bar, TEXT shows text only.
     */
    val displayStyle: OptionListConfig<DisplayStyle> = enumConfig(
        name = "displayStyle",
        default = DisplayStyle.BAR,
        displayName = "Display Style",
        comment = "Bar or text display"
    ).onChange { onConfigUpdate(displayStyle) }

    /**
     * Show all classes instead of just current.
     */
    val showAllClasses: BooleanConfig = booleanConfig(
        name = "showAllClasses",
        default = false,
        displayName = "Show All Classes",
        comment = "Show all classes instead of current"
    ).onChange { onConfigUpdate(showAllClasses) }

    /**
     * Show level number next to class name.
     */
    val showLevel: BooleanConfig = booleanConfig(
        name = "showLevel",
        default = true,
        displayName = "Show Level",
        comment = "Show level next to class name"
    ).onChange { onConfigUpdate(showLevel) }

    /**
     * Show percentage instead of absolute XP values.
     */
    val showPercentage: BooleanConfig = booleanConfig(
        name = "showPercentage",
        default = false,
        displayName = "Show Percentage",
        comment = "Show percentage instead of XP"
    ).onChange { onConfigUpdate(showPercentage) }

    /**
     * How to calculate percentage: level, total, or tier progress.
     */
    val percentageMode: OptionListConfig<PercentageMode> = enumConfig(
        name = "percentageMode",
        default = PercentageMode.LEVEL_PROGRESS,
        displayName = "Percentage Mode",
        comment = "Level = within level, Total = toward max, Tier = factors in other classes"
    ).onChange { onConfigUpdate(percentageMode) }

    // === XP Tracking Options ===

    /**
     * Show XP rate (XP per hour) based on recent activity.
     */
    val showXpRate: BooleanConfig = booleanConfig(
        name = "showXpRate",
        default = true,
        displayName = "Show XP Rate",
        comment = "Show XP per hour based on recent activity"
    ).onChange { onConfigUpdate(showXpRate) }

    /**
     * Show estimated time to next level.
     */
    val showTimeToLevel: BooleanConfig = booleanConfig(
        name = "showTimeToLevel",
        default = true,
        displayName = "Show Time to Level",
        comment = "Show estimated time to reach next level"
    ).onChange { onConfigUpdate(showTimeToLevel) }

    /**
     * Show session XP gained (total XP earned since joining).
     */
    val showSessionXp: BooleanConfig = booleanConfig(
        name = "showSessionXp",
        default = false,
        displayName = "Show Session XP",
        comment = "Show total XP gained this session"
    ).onChange { onConfigUpdate(showSessionXp) }

    // === Bar Options (only used when displayStyle is BAR) ===

    /**
     * Bar width in pixels.
     */
    val barWidth: IntegerConfig = intConfig(
        name = "barWidth",
        default = 120,
        min = 50,
        max = 200,
        displayName = "Bar Width",
        comment = "Progress bar width"
    ).onChange { onConfigUpdate(barWidth) }

    /**
     * Bar height in pixels.
     */
    val barHeight: IntegerConfig = intConfig(
        name = "barHeight",
        default = 10,
        min = 4,
        max = 16,
        displayName = "Bar Height",
        comment = "Progress bar height"
    ).onChange { onConfigUpdate(barHeight) }

    /**
     * Progress bar background color (ARGB hex).
     */
    val barBackgroundColor: ColorConfig = colorConfig(
        name = "barBackgroundColor",
        default = 0xFF333333.toInt(),
        displayName = "Bar Background",
        comment = "Progress bar background color"
    ).onChange { onConfigUpdate(barBackgroundColor) }

    /**
     * Progress bar fill color (ARGB hex).
     */
    val barFillColor: ColorConfig = colorConfig(
        name = "barFillColor",
        default = 0xFF55FF55.toInt(),
        displayName = "Bar Fill",
        comment = "Progress bar fill color"
    ).onChange { onConfigUpdate(barFillColor) }

    /**
     * Progress bar border color (ARGB hex).
     */
    val barBorderColor: ColorConfig = colorConfig(
        name = "barBorderColor",
        default = 0xFF000000.toInt(),
        displayName = "Bar Border",
        comment = "Progress bar border color"
    ).onChange { onConfigUpdate(barBorderColor) }

    /**
     * Text color (ARGB hex).
     */
    val textColor: ColorConfig = colorConfig(
        name = "textColor",
        default = 0xFFFFFFFF.toInt(),
        displayName = "Text Color",
        comment = "Text color"
    ).onChange { onConfigUpdate(textColor) }

    override fun getConfigs(): List<ConfigOption<*>> = listOf(
        enabled, displayStyle, showAllClasses, showLevel, showPercentage, percentageMode,
        showXpRate, showTimeToLevel, showSessionXp,
        barWidth, barHeight, barBackgroundColor, barFillColor, barBorderColor, textColor
    )

    override fun onConfigUpdate(config: ConfigOption<*>) {
        CivutilsMod.configManager.markDirty()
    }

    override fun shouldRender(): Boolean {
        if (!super.shouldRender()) return false
        return ClassModel.hasData
    }

    override fun render(guiGraphics: GuiGraphics, tickDelta: Float) {
        // Calculate and update size before rendering
        updateSize()

        val y = getRenderY()
        val x = getRenderX()

        if (showAllClasses.value) {
            renderAllClasses(guiGraphics, x, y)
        } else {
            renderCurrentClass(guiGraphics, x, y)
        }
    }

    /**
     * Update overlay size based on current config and content.
     */
    private fun updateSize() {
        val font = mc.font
        val isBar = displayStyle.value == DisplayStyle.BAR

        // Calculate width - include stats text width if enabled
        val sampleXpText = if (showPercentage.value) "100%" else "9999/9999 XP"
        val xpTextWidth = font.width(sampleXpText)

        // Sample stats text for width calculation
        val sampleStatsWidth = if (hasAnyStatsEnabled()) {
            font.width("1.2K/h | 5m 30s | +9999 XP")
        } else 0

        val contentWidth = if (isBar) {
            val inlineWidth = barWidth.value + 4 + xpTextWidth
            maxOf(barWidth.value, inlineWidth, font.width("Blacksmith (99)"), sampleStatsWidth)
        } else {
            maxOf(xpTextWidth, font.width("Blacksmith (99)"), sampleStatsWidth)
        }

        // Calculate height
        val classes = if (showAllClasses.value) {
            ClassModel.classes.values.filter { it.xpForLevel > 0 }
        } else {
            val current = ClassModel.currentClassName
            if (current.isNotEmpty()) ClassModel.getClass(current)?.let { listOf(it) } ?: emptyList()
            else emptyList()
        }

        // Current class gets stats line, others don't
        val currentClassName = ClassModel.currentClassName
        val totalHeight = if (classes.isEmpty()) {
            font.lineHeight
        } else {
            var height = 0
            for ((index, classInfo) in classes.withIndex()) {
                val isCurrent = classInfo.name == currentClassName
                height += calculateEntryHeight(includeStats = isCurrent)
                if (index < classes.size - 1) height += 6 // spacing between entries
            }
            height
        }

        size.width = contentWidth + 4
        size.height = totalHeight + 4
    }

    /**
     * Calculate height of a single class entry.
     * @param includeStats Whether to include the stats line (XP rate, time-to-level)
     */
    private fun calculateEntryHeight(includeStats: Boolean = false): Int {
        val font = mc.font
        val isBar = displayStyle.value == DisplayStyle.BAR

        var height = if (isBar) {
            // Name line + bar (with inline XP if fits, otherwise +1 line)
            val xpText = if (showPercentage.value) "100%" else "9999/9999 XP"
            val inlineWidth = barWidth.value + 4 + font.width(xpText)
            val fitsInline = inlineWidth <= size.width

            if (fitsInline) {
                font.lineHeight + 2 + barHeight.value
            } else {
                font.lineHeight + 2 + barHeight.value + 2 + font.lineHeight
            }
        } else {
            // Name line + XP line
            font.lineHeight + 2 + font.lineHeight
        }

        // Add stats line height if enabled and showing stats
        if (includeStats && hasAnyStatsEnabled()) {
            height += 2 + font.lineHeight
        }

        return height
    }

    /**
     * Check if any XP stats options are enabled.
     */
    private fun hasAnyStatsEnabled(): Boolean {
        return showXpRate.value || showTimeToLevel.value || showSessionXp.value
    }

    private fun renderCurrentClass(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val font = mc.font

        val className = ClassModel.currentClassName
        val classInfo = if (className.isNotEmpty()) ClassModel.getClass(className) else null

        if (classInfo == null || classInfo.xpForLevel <= 0) {
            guiGraphics.drawString(font, "§8Open /class for details", x, y, textColor.value, true)
            return
        }

        renderClassEntry(guiGraphics, x, y, classInfo, isCurrent = true)
    }

    private fun renderAllClasses(guiGraphics: GuiGraphics, x: Int, y: Int) {
        var currentY = y
        val font = mc.font

        val classes = ClassModel.classes.values
            .filter { it.xpForLevel > 0 }
            .sortedByDescending { it.totalXp }

        if (classes.isEmpty()) {
            guiGraphics.drawString(font, "§8Open /class for details", x, currentY, textColor.value, true)
            return
        }

        for (classInfo in classes) {
            val isCurrent = classInfo.name == ClassModel.currentClassName
            val height = renderClassEntry(guiGraphics, x, currentY, classInfo, isCurrent)
            currentY += height + 6
        }
    }

    /**
     * Renders a single class entry and returns the height used.
     */
    private fun renderClassEntry(guiGraphics: GuiGraphics, x: Int, y: Int, classInfo: ClassInfo, isCurrent: Boolean): Int {
        val font = mc.font
        var currentY = y
        val isBar = displayStyle.value == DisplayStyle.BAR

        // Class name with optional level
        val nameColor = if (isCurrent) "e" else "f"
        val levelSuffix = if (showLevel.value && classInfo.levelName.isNotEmpty()) " §7(${classInfo.level})" else ""
        guiGraphics.drawString(font, "§$nameColor${classInfo.name}$levelSuffix", x, currentY, textColor.value, true)
        currentY += font.lineHeight + 2

        // Calculate percentage based on mode
        val percentage = when (percentageMode.value) {
            PercentageMode.LEVEL_PROGRESS -> classInfo.levelProgressPercent
            PercentageMode.TOTAL_PROGRESS -> classInfo.totalProgressPercent
            PercentageMode.TIER_PROGRESS -> classInfo.tierProgressPercent
        }

        // XP display text
        val xpText = if (showPercentage.value) {
            "§7${percentage}%"
        } else {
            "§7${classInfo.currentXp}§8/§7${classInfo.xpForLevel} XP"
        }

        if (isBar) {
            val progress = percentage / 100f
            val xpTextWidth = font.width(xpText.replace("§7", "").replace("§8", ""))
            val inlineWidth = barWidth.value + 4 + xpTextWidth

            // Check if XP text fits inline after bar
            if (inlineWidth <= size.width) {
                // Inline: bar + XP text on same line
                drawProgressBar(guiGraphics, x, currentY, barWidth.value, barHeight.value, progress)
                guiGraphics.drawString(font, xpText, x + barWidth.value + 4, currentY + (barHeight.value - font.lineHeight) / 2, textColor.value, true)
                currentY += barHeight.value
            } else {
                // Stacked: bar on one line, XP text below
                drawProgressBar(guiGraphics, x, currentY, barWidth.value, barHeight.value, progress)
                currentY += barHeight.value + 2
                guiGraphics.drawString(font, xpText, x, currentY, textColor.value, true)
                currentY += font.lineHeight
            }
        } else {
            // Text only mode
            guiGraphics.drawString(font, xpText, x, currentY, textColor.value, true)
            currentY += font.lineHeight
        }

        // XP rate and time-to-level (only for current class to avoid clutter)
        if (isCurrent) {
            currentY = renderXpStats(guiGraphics, x, currentY, classInfo)
        }

        return currentY - y
    }

    /**
     * Renders XP rate, time-to-level, and session stats.
     * Returns the new Y position after rendering.
     */
    private fun renderXpStats(guiGraphics: GuiGraphics, x: Int, y: Int, classInfo: ClassInfo): Int {
        val font = mc.font
        var currentY = y

        // Build stats line(s)
        val stats = mutableListOf<String>()

        // XP rate
        if (showXpRate.value) {
            val xpRate = classInfo.calculateXpPerHour()
            if (xpRate != null) {
                stats.add("§a${ClassModel.formatXpPerHour(xpRate)}")
            }
        }

        // Time to level
        if (showTimeToLevel.value) {
            val timeToLevel = classInfo.estimateTimeToLevel()
            if (timeToLevel != null) {
                stats.add("§b${ClassModel.formatDuration(timeToLevel)}")
            }
        }

        // Session XP
        if (showSessionXp.value && classInfo.sessionXpGained > 0) {
            val sessionXp = classInfo.sessionXpGained.toInt()
            stats.add("§d+${sessionXp} XP")
        }

        // Render stats if we have any
        if (stats.isNotEmpty()) {
            currentY += 2
            val statsText = "§8${stats.joinToString(" §8| ")}"
            guiGraphics.drawString(font, statsText, x, currentY, textColor.value, true)
            currentY += font.lineHeight
        }

        return currentY
    }

    override fun renderPreview(guiGraphics: GuiGraphics, tickDelta: Float) {
        updateSize()

        val font = mc.font
        var y = getRenderY()
        val x = getRenderX()
        val isBar = displayStyle.value == DisplayStyle.BAR

        // Class name with optional level
        val levelSuffix = if (showLevel.value) " §7(0)" else ""
        guiGraphics.drawString(font, "§fGuardsman$levelSuffix", x, y, textColor.value, true)
        y += font.lineHeight + 2

        // XP display based on percentage mode
        // Preview: Level 2, totalXp = 3500, 50% of total XP in this class
        // Level progress: (3500-2221)/(5962-2221) = 1279/3741 = 34% within level
        // Total progress: 3500/36219 = 9.7% toward max
        // Tier progress: 34% * 50% = 17% (other classes bring it down)
        val xpText = if (showPercentage.value) {
            when (percentageMode.value) {
                PercentageMode.LEVEL_PROGRESS -> "§734%"
                PercentageMode.TOTAL_PROGRESS -> "§79%"
                PercentageMode.TIER_PROGRESS -> "§717%"
            }
        } else {
            "§71279§8/§73741 XP"
        }

        if (isBar) {
            val xpTextWidth = font.width(xpText.replace("§7", "").replace("§8", ""))
            val inlineWidth = barWidth.value + 4 + xpTextWidth
            val previewProgress = when (percentageMode.value) {
                PercentageMode.LEVEL_PROGRESS -> 0.34f
                PercentageMode.TOTAL_PROGRESS -> 0.09f
                PercentageMode.TIER_PROGRESS -> 0.17f
            }

            if (inlineWidth <= size.width) {
                drawProgressBar(guiGraphics, x, y, barWidth.value, barHeight.value, previewProgress)
                guiGraphics.drawString(font, xpText, x + barWidth.value + 4, y + (barHeight.value - font.lineHeight) / 2, textColor.value, true)
                y += barHeight.value
            } else {
                drawProgressBar(guiGraphics, x, y, barWidth.value, barHeight.value, previewProgress)
                y += barHeight.value + 2
                guiGraphics.drawString(font, xpText, x, y, textColor.value, true)
                y += font.lineHeight
            }
        } else {
            guiGraphics.drawString(font, xpText, x, y, textColor.value, true)
            y += font.lineHeight
        }

        // Preview stats line
        if (hasAnyStatsEnabled()) {
            y += 2
            val previewStats = mutableListOf<String>()
            if (showXpRate.value) previewStats.add("§a1.2K/h")
            if (showTimeToLevel.value) previewStats.add("§b5m 30s")
            if (showSessionXp.value) previewStats.add("§d+1234 XP")

            if (previewStats.isNotEmpty()) {
                val statsText = "§8${previewStats.joinToString(" §8| ")}"
                guiGraphics.drawString(font, statsText, x, y, textColor.value, true)
            }
        }
    }

    private fun drawProgressBar(guiGraphics: GuiGraphics, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)

        // Background
        guiGraphics.fill(x, y, x + width, y + height, barBackgroundColor.value)

        // Fill
        val fillWidth = (width * clampedProgress).toInt()
        if (fillWidth > 0) {
            guiGraphics.fill(x, y, x + fillWidth, y + height, barFillColor.value)
        }

        // Border (1px)
        guiGraphics.fill(x, y, x + width, y + 1, barBorderColor.value)
        guiGraphics.fill(x, y + height - 1, x + width, y + height, barBorderColor.value)
        guiGraphics.fill(x, y, x + 1, y + height, barBorderColor.value)
        guiGraphics.fill(x + width - 1, y, x + width, y + height, barBorderColor.value)
    }
}
