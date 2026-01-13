package xyz.nim.civutils.models

import net.minecraft.world.BossEvent
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.BossBarAction
import xyz.nim.civutils.core.event.BossBarEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.model.Model
import java.util.UUID

/**
 * Tracks combat timer state from the Combat-Log Timer BossBar.
 */
data class CombatTimerState(
    var active: Boolean = false,
    var progress: Float = 0f,       // 0.0 to 1.0, where 1.0 = full timer, decreases over time
    var lastUpdateTime: Long = 0,
    var uuid: UUID? = null
)

/**
 * Tracks bed healing state from the healing progress BossBar.
 */
data class BedHealingState(
    var active: Boolean = false,
    var progress: Float = 0f,       // 0.0 to 1.0, where 1.0 = fully healed
    var lastUpdateTime: Long = 0,
    var uuid: UUID? = null,
    // For time estimation
    var startProgress: Float = 0f,
    var startTime: Long = 0,
    var progressRate: Float = 0f    // Progress per second
)

/**
 * Model that tracks server BossBar data for combat timer and bed healing.
 *
 * Combat-Log Timer:
 * - Name contains "Combat-Log Timer" or "Combat Log"
 * - Red color, segmented style
 * - Progress decreases over time (starts at 1.0, counts down to 0.0)
 *
 * Bed Healing:
 * - Name typically contains "Healing" or healing progress text
 * - Progress increases as healing completes (0.0 to 1.0)
 */
object BossBarModel : Model() {

    /** Current combat timer state */
    val combatTimer = CombatTimerState()

    /** Current bed healing state */
    val bedHealing = BedHealingState()

    /** Combat timer duration in seconds (server default is typically 30s) */
    var combatTimerDurationSeconds: Float = 30f
        private set

    /**
     * Check if combat timer is active (with auto-expiry).
     * Returns false if no update received in 500ms.
     */
    fun isCombatTimerActive(): Boolean {
        if (!combatTimer.active) return false
        // Auto-expire if no update in 500ms (bar updates frequently while active)
        if (System.currentTimeMillis() - combatTimer.lastUpdateTime > 500) {
            combatTimer.active = false
            combatTimer.progress = 0f
            return false
        }
        return true
    }

    /**
     * Get estimated seconds remaining on combat timer.
     * Returns null if combat timer is not active.
     */
    fun getCombatTimerSecondsRemaining(): Float? {
        if (!isCombatTimerActive()) return null
        return combatTimer.progress * combatTimerDurationSeconds
    }

    /**
     * Check if bed healing is active (with auto-expiry).
     * Returns false if no update received in 500ms or healing is complete.
     */
    fun isBedHealingActive(): Boolean {
        if (!bedHealing.active) return false
        // Auto-expire if no update in 500ms (bar updates frequently while active)
        if (System.currentTimeMillis() - bedHealing.lastUpdateTime > 500) {
            resetBedHealing()
            return false
        }
        // Also expire if healing completed (progress >= 99%)
        if (bedHealing.progress >= 0.99f) {
            resetBedHealing()
            return false
        }
        return true
    }

    private fun resetBedHealing() {
        bedHealing.active = false
        bedHealing.progress = 0f
        bedHealing.progressRate = 0f
        bedHealing.startTime = 0
        bedHealing.startProgress = 0f
    }

    /**
     * Get bed healing progress as percentage (0-100).
     * Returns null if not healing.
     */
    fun getBedHealingPercent(): Int? {
        if (!isBedHealingActive()) return null
        return (bedHealing.progress * 100).toInt()
    }

    /**
     * Get estimated seconds remaining for bed healing to complete.
     * Returns null if not healing or rate cannot be determined.
     */
    fun getBedHealingSecondsRemaining(): Float? {
        if (!isBedHealingActive()) return null
        if (bedHealing.progressRate <= 0f) return null

        val remaining = 1f - bedHealing.progress
        if (remaining <= 0f) return 0f

        return remaining / bedHealing.progressRate
    }

    // === Vanilla Bar Hiding ===

    /** Whether to hide combat timer vanilla bar */
    var hideCombatTimerBar: Boolean = false

    /** Whether to hide bed healing vanilla bar */
    var hideBedHealingBar: Boolean = false

    /**
     * Check if a boss bar with the given UUID should be hidden.
     */
    fun shouldHideBar(uuid: UUID): Boolean {
        if (hideCombatTimerBar && combatTimer.uuid == uuid) return true
        if (hideBedHealingBar && bedHealing.uuid == uuid) return true
        return false
    }

    override fun reset() {
        combatTimer.active = false
        combatTimer.progress = 0f
        combatTimer.lastUpdateTime = 0
        combatTimer.uuid = null

        bedHealing.active = false
        bedHealing.progress = 0f
        bedHealing.lastUpdateTime = 0
        bedHealing.uuid = null
        bedHealing.startProgress = 0f
        bedHealing.startTime = 0
        bedHealing.progressRate = 0f
    }

    @Subscribe
    fun onBossBarEvent(event: BossBarEvent) {
        val name = event.name.lowercase()

        when {
            // Combat timer detection
            isCombatTimerBar(name, event.color, event.overlay) -> handleCombatTimerEvent(event)
            // Bed healing detection
            isBedHealingBar(name) -> handleBedHealingEvent(event)
        }
    }

    /**
     * Check if this BossBar is the combat log timer.
     * Server uses red color and segmented (NOTCHED_6, NOTCHED_10, NOTCHED_12, or NOTCHED_20) overlay.
     */
    private fun isCombatTimerBar(
        name: String,
        color: BossEvent.BossBarColor,
        overlay: BossEvent.BossBarOverlay
    ): Boolean {
        // Check name patterns
        val nameMatcher = name.contains("combat") && (name.contains("log") || name.contains("timer"))

        // Combat timer is typically red with segments
        val styleMatcher = color == BossEvent.BossBarColor.RED &&
                overlay != BossEvent.BossBarOverlay.PROGRESS

        return nameMatcher || (styleMatcher && name.contains("combat"))
    }

    /**
     * Check if this BossBar is the bed healing progress bar.
     */
    private fun isBedHealingBar(name: String): Boolean {
        return name.contains("heal") || name.contains("resting") || name.contains("recovering")
    }

    private fun handleCombatTimerEvent(event: BossBarEvent) {
        when (event.action) {
            BossBarAction.ADD, BossBarAction.UPDATE -> {
                combatTimer.active = true
                combatTimer.progress = event.progress
                combatTimer.lastUpdateTime = System.currentTimeMillis()
                combatTimer.uuid = event.uuid
                CivutilsMod.logger.debug("BossBarModel: Combat timer update - progress=${event.progress}")
            }
            BossBarAction.REMOVE -> {
                if (combatTimer.uuid == event.uuid) {
                    combatTimer.active = false
                    combatTimer.progress = 0f
                    combatTimer.uuid = null
                    CivutilsMod.logger.debug("BossBarModel: Combat timer removed")
                }
            }
        }
    }

    private fun handleBedHealingEvent(event: BossBarEvent) {
        when (event.action) {
            BossBarAction.ADD -> {
                // New healing session - reset tracking
                initBedHealingTracking(event)
                CivutilsMod.logger.debug("BossBarModel: Bed healing started - progress=${event.progress}")
            }
            BossBarAction.UPDATE -> {
                val now = System.currentTimeMillis()

                // If we missed the ADD event or this is a new session, initialize tracking
                if (bedHealing.startTime == 0L || bedHealing.uuid != event.uuid) {
                    initBedHealingTracking(event)
                    CivutilsMod.logger.debug("BossBarModel: Bed healing initialized from UPDATE - progress=${event.progress}")
                    return
                }

                val previousProgress = bedHealing.progress
                val previousTime = bedHealing.lastUpdateTime

                bedHealing.active = true
                bedHealing.progress = event.progress
                bedHealing.lastUpdateTime = now

                // Calculate rate from this update (instantaneous rate)
                val timeDelta = (now - previousTime) / 1000f
                val progressDelta = event.progress - previousProgress

                if (timeDelta > 0.05f && progressDelta > 0f) {
                    // Calculate instantaneous rate and smooth it with existing rate
                    val instantRate = progressDelta / timeDelta
                    if (bedHealing.progressRate <= 0f) {
                        bedHealing.progressRate = instantRate
                    } else {
                        // Exponential moving average for smoothing
                        bedHealing.progressRate = bedHealing.progressRate * 0.7f + instantRate * 0.3f
                    }
                    CivutilsMod.logger.debug("BossBarModel: Bed healing update - progress=${event.progress}, instantRate=$instantRate, smoothedRate=${bedHealing.progressRate}/s")
                }
            }
            BossBarAction.REMOVE -> {
                if (bedHealing.uuid == event.uuid) {
                    bedHealing.active = false
                    bedHealing.progress = 0f
                    bedHealing.uuid = null
                    bedHealing.startProgress = 0f
                    bedHealing.startTime = 0
                    bedHealing.progressRate = 0f
                    CivutilsMod.logger.debug("BossBarModel: Bed healing removed")
                }
            }
        }
    }

    private fun initBedHealingTracking(event: BossBarEvent) {
        val now = System.currentTimeMillis()
        bedHealing.active = true
        bedHealing.progress = event.progress
        bedHealing.lastUpdateTime = now
        bedHealing.uuid = event.uuid
        bedHealing.startProgress = event.progress
        bedHealing.startTime = now
        bedHealing.progressRate = 0f
    }
}
