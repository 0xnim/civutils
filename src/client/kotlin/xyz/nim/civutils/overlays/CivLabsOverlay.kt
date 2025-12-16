package xyz.nim.civutils.overlays

import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.config.Persisted
import xyz.nim.civutils.core.overlay.OverlayPosition
import xyz.nim.civutils.core.overlay.OverlaySize
import xyz.nim.civutils.core.overlay.TextOverlay
import xyz.nim.civutils.models.CivLabsModel
import java.text.DecimalFormat

/**
 * Display mode for CivLabs stats.
 */
enum class CivLabsDisplayMode {
    /** Show everything: total (change) class */
    FULL,
    /** Show compact: total class */
    COMPACT,
    /** Show detailed with labels */
    DETAILED
}

/**
 * CivLabs Actionbar Overlay: Shows CivLabs stats in a clean format.
 *
 * Parses the actionbar format: "42201.0 (+13.0) Guardsman"
 * and displays it as: "42201.0 (+13.0) Guardsman"
 */
class CivLabsOverlay : TextOverlay(
    position = OverlayPosition.topCenter(offsetX = 0, offsetY = 5),
    size = OverlaySize(width = 200, height = 30)
) {
    override val displayName = "CivLabs Stats"

    /**
     * Display format for the stats.
     */
    @Persisted
    val displayMode = Config(defaultValue = CivLabsDisplayMode.FULL)

    /**
     * Color for positive changes.
     */
    @Persisted
    val positiveColor = Config(defaultValue = "a") // Green

    /**
     * Color for negative changes.
     */
    @Persisted
    val negativeColor = Config(defaultValue = "c") // Red

    private val decimalFormat = DecimalFormat("#,##0.0")

    override fun getTemplate(): String {
        if (!CivLabsModel.hasData) return ""

        // Check if data is stale (more than 10 seconds old)
        val dataAge = System.currentTimeMillis() - CivLabsModel.lastUpdateTime
        if (dataAge > 10000) return ""

        return when (displayMode.value) {
            CivLabsDisplayMode.FULL -> buildFullTemplate()
            CivLabsDisplayMode.COMPACT -> buildCompactTemplate()
            CivLabsDisplayMode.DETAILED -> buildDetailedTemplate()
        }
    }

    override fun getPreviewTemplate(): String {
        return when (displayMode.value) {
            CivLabsDisplayMode.FULL -> "§f42,201.0 §7(§a+13.0§7) §eGuardsman"
            CivLabsDisplayMode.COMPACT -> "§f42,201.0 §eGuardsman"
            CivLabsDisplayMode.DETAILED -> """
                §7Score: §f42,201.0
                §7Change: §a+13.0
                §7Class: §eGuardsman
            """.trimIndent()
        }
    }

    private fun buildFullTemplate(): String {
        val total = decimalFormat.format(CivLabsModel.total)
        val change = CivLabsModel.change
        val changeColor = if (change >= 0) positiveColor.value else negativeColor.value
        val changeSign = if (change >= 0) "+" else ""
        val changeFormatted = decimalFormat.format(change)
        val className = CivLabsModel.className

        return "§f$total §7(§$changeColor$changeSign$changeFormatted§7) §e$className"
    }

    private fun buildCompactTemplate(): String {
        val total = decimalFormat.format(CivLabsModel.total)
        val className = CivLabsModel.className

        return "§f$total §e$className"
    }

    private fun buildDetailedTemplate(): String {
        val total = decimalFormat.format(CivLabsModel.total)
        val change = CivLabsModel.change
        val changeColor = if (change >= 0) positiveColor.value else negativeColor.value
        val changeSign = if (change >= 0) "+" else ""
        val changeFormatted = decimalFormat.format(change)
        val className = CivLabsModel.className

        return """
            §7Score: §f$total
            §7Change: §$changeColor$changeSign$changeFormatted
            §7Class: §e$className
        """.trimIndent()
    }
}
