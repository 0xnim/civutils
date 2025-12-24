package xyz.nim.civutils.overlays

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.config.Persisted
import xyz.nim.civutils.core.config.intConfig
import xyz.nim.civutils.core.overlay.Overlay
import xyz.nim.civutils.core.overlay.OverlayPosition
import xyz.nim.civutils.core.overlay.OverlaySize
import xyz.nim.civutils.models.ClassModel
import java.text.DecimalFormat

/**
 * ClassXpOverlay: Shows class XP progress with visual bars.
 *
 * Data sources:
 * - Actionbar: Real-time total XP and changes (updates constantly)
 * - /class menu: Detailed XP breakdown (updates when opened)
 */
class ClassXpOverlay : Overlay(
    position = OverlayPosition.topRight(offsetX = -5, offsetY = 50),
    size = OverlaySize(width = 160, height = 60)
) {
    override val displayName = "Class XP"

    private val mc: MinecraftClient get() = MinecraftClient.getInstance()
    private val decimalFormat = DecimalFormat("#,##0.0")

    /**
     * Bar width in pixels.
     */
    @Persisted
    val barWidth = intConfig(default = 120, min = 50, max = 200)

    /**
     * Bar height in pixels.
     */
    @Persisted
    val barHeight = intConfig(default = 10, min = 4, max = 16)

    /**
     * Show all classes (from /class menu) instead of just current.
     */
    @Persisted
    val showAllClasses = Config(defaultValue = false)

    // Colors
    private val barBackgroundColor = 0xFF333333.toInt()
    private val barFillColor = 0xFF55FF55.toInt()  // Green
    private val barBorderColor = 0xFF000000.toInt()
    private val textColor = 0xFFFFFFFF.toInt()

    override fun shouldRender(): Boolean {
        if (!super.shouldRender()) return false
        return ClassModel.hasData
    }

    override fun render(context: DrawContext, tickDelta: Float) {
        val textRenderer = mc.textRenderer
        var y = getRenderY()
        val x = getRenderX()

        if (showAllClasses.value) {
            // Show all classes from /class menu
            renderAllClasses(context, x, y)
        } else {
            // Show only current class from actionbar
            renderCurrentClass(context, x, y)
        }
    }

    private fun renderCurrentClass(context: DrawContext, x: Int, y: Int) {
        var currentY = y
        val textRenderer = mc.textRenderer

        val className = ClassModel.currentClassName
        if (className.isEmpty()) {
            context.drawText(textRenderer, "§7No class data", x, currentY, textColor, true)
            return
        }

        val classInfo = ClassModel.getClass(className)

        // Class name
        context.drawText(textRenderer, "§f$className", x, currentY, textColor, true)
        currentY += textRenderer.fontHeight + 3

        // Progress bar and XP text (if we have detailed data from /class menu)
        if (classInfo != null && classInfo.xpForLevel > 0) {
            val progress = classInfo.xpPercent / 100f
            drawProgressBar(context, x, currentY, barWidth.value, barHeight.value, progress)
            currentY += barHeight.value + 2

            context.drawText(textRenderer, "§7${classInfo.currentXp}§8/§7${classInfo.xpForLevel} XP", x, currentY, textColor, true)
        } else {
            // No detailed data, show hint
            context.drawText(textRenderer, "§8Open /class for bar", x, currentY, textColor, true)
        }
    }

    private fun renderAllClasses(context: DrawContext, x: Int, y: Int) {
        var currentY = y
        val textRenderer = mc.textRenderer

        val classes = ClassModel.classes.values
            .filter { it.totalXp > 0 || it.currentXp > 0 }
            .sortedByDescending { it.totalXp }

        if (classes.isEmpty()) {
            context.drawText(textRenderer, "§7No class data", x, currentY, textColor, true)
            return
        }

        for (classInfo in classes) {
            val isCurrent = classInfo.name == ClassModel.currentClassName
            val nameColor = if (isCurrent) "e" else "f"  // Yellow for current, white for others

            // Class name
            val levelSuffix = if (classInfo.levelName.isNotEmpty()) " §7(${classInfo.level})" else ""
            context.drawText(textRenderer, "§$nameColor${classInfo.name}$levelSuffix", x, currentY, textColor, true)
            currentY += textRenderer.fontHeight + 1

            // XP with change (only for current class)
            if (isCurrent && classInfo.lastChange != 0.0) {
                val changeColor = if (classInfo.lastChange >= 0) "a" else "c"
                val changeSign = if (classInfo.lastChange >= 0) "+" else ""
                val xpText = "§7${decimalFormat.format(classInfo.totalXp)} §${changeColor}(${changeSign}${decimalFormat.format(classInfo.lastChange)})"
                context.drawText(textRenderer, xpText, x, currentY, textColor, true)
            } else {
                context.drawText(textRenderer, "§7${decimalFormat.format(classInfo.totalXp)} XP", x, currentY, textColor, true)
            }
            currentY += textRenderer.fontHeight + 4
        }
    }

    override fun renderPreview(context: DrawContext, tickDelta: Float) {
        val textRenderer = mc.textRenderer
        var y = getRenderY()
        val x = getRenderX()

        // Preview: class name, bar, XP
        context.drawText(textRenderer, "§fGuardsman", x, y, textColor, true)
        y += textRenderer.fontHeight + 3

        drawProgressBar(context, x, y, barWidth.value, barHeight.value, 0.4f)
        y += barHeight.value + 2

        context.drawText(textRenderer, "§7256§8/§7636 XP", x, y, textColor, true)
    }

    private fun drawProgressBar(context: DrawContext, x: Int, y: Int, width: Int, height: Int, progress: Float) {
        val clampedProgress = progress.coerceIn(0f, 1f)

        // Background
        context.fill(x, y, x + width, y + height, barBackgroundColor)

        // Fill
        val fillWidth = (width * clampedProgress).toInt()
        if (fillWidth > 0) {
            context.fill(x, y, x + fillWidth, y + height, barFillColor)
        }

        // Border (1px)
        context.fill(x, y, x + width, y + 1, barBorderColor)
        context.fill(x, y + height - 1, x + width, y + height, barBorderColor)
        context.fill(x, y, x + 1, y + height, barBorderColor)
        context.fill(x + width - 1, y, x + width, y + height, barBorderColor)
    }
}
