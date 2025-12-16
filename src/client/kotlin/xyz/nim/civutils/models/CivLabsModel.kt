package xyz.nim.civutils.models

import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.ActionBarMessageEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.model.Model

/**
 * Model that parses and tracks CivLabs actionbar stats.
 *
 * Expected format: "42201.0 (+13.0) Guardsman"
 * Parses into: total, change (positive or negative), class name
 */
object CivLabsModel : Model() {

    /** Total stat value (e.g., 42201.0) */
    var total: Double = 0.0
        private set

    /** Change since last update (e.g., +13.0 or -5.0) */
    var change: Double = 0.0
        private set

    /** Class name (e.g., "Guardsman") */
    var className: String = ""
        private set

    /** Raw actionbar message for debugging */
    var rawMessage: String = ""
        private set

    /** Whether we have valid CivLabs data */
    var hasData: Boolean = false
        private set

    /** Timestamp of last successful parse */
    var lastUpdateTime: Long = 0
        private set

    // Regex pattern to match "42201.0 (+13.0) Guardsman" format
    // Also handles negative changes like "(-5.0)"
    private val civLabsPattern = Regex("""^([\d.]+)\s+\(([+-][\d.]+)\)\s+(.+)$""")

    override fun reset() {
        total = 0.0
        change = 0.0
        className = ""
        rawMessage = ""
        hasData = false
        lastUpdateTime = 0
    }

    @Subscribe
    fun onActionBarMessage(event: ActionBarMessageEvent) {
        val message = event.rawMessage.trim()
        rawMessage = message

        // Try to parse CivLabs format
        val match = civLabsPattern.matchEntire(message)
        if (match != null) {
            try {
                total = match.groupValues[1].toDouble()
                change = match.groupValues[2].toDouble()
                className = match.groupValues[3].trim()
                hasData = true
                lastUpdateTime = System.currentTimeMillis()

                CivutilsMod.logger.debug("CivLabs parsed: total=$total, change=$change, class=$className")
            } catch (e: NumberFormatException) {
                CivutilsMod.logger.debug("Failed to parse CivLabs numbers: $message")
            }
        }
    }
}
