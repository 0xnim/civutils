package xyz.nim.civutils.models

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.ActionBarMessageEvent
import xyz.nim.civutils.core.event.ClassXpChannelEvent
import xyz.nim.civutils.core.event.ContainerUpdateEvent
import xyz.nim.civutils.core.event.ScreenOpenEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.model.Model
import xyz.nim.civutils.core.network.ClassChannelData
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.math.floor
import kotlin.math.pow

/**
 * A single XP gain/loss event for tracking history.
 */
data class XpEvent(
    val timestamp: Long,
    val xpChange: Double,
    val totalXp: Double
)

/**
 * Data class representing a single class/specialization.
 */
data class ClassInfo(
    val name: String,
    var tierPercent: Int = 0,        // "You are X% progressed through this tier" (from server)
    var levelName: String = "",       // e.g., "Novice"
    var level: Int = 0,               // e.g., 0
    var currentXp: Int = 0,           // Current XP in this level (from /class menu)
    var xpForLevel: Int = 0,          // XP needed for next level (from /class menu: currentXp + missing)
    var totalXp: Double = 0.0,        // Total accumulated XP (from actionbar)
    var lastChange: Double = 0.0,     // Last XP change (+/-) from actionbar
) {
    /**
     * XP progress as percentage (0-100) within current level.
     * Uses /class menu data (currentXp / xpForLevel).
     */
    val xpPercent: Int
        get() = if (xpForLevel > 0) ((currentXp.toDouble() / xpForLevel) * 100).toInt() else 0

    /**
     * Level progress percentage (0-100) calculated from totalXp using server formula.
     * Shows progress within current level toward next level.
     */
    val levelProgressPercent: Int
        get() = if (totalXp > 0) ClassModel.calculateLevelProgress(totalXp) else xpPercent

    /**
     * Total progress percentage (0-100) toward max level (level 5).
     * Shows overall progression from 0 XP to max XP.
     */
    val totalProgressPercent: Int
        get() = ClassModel.calculateTotalProgress(totalXp)

    /**
     * Calculated level from totalXp using server formula.
     */
    val calculatedLevel: Int
        get() = if (totalXp > 0) ClassModel.getLevelFromTotalXp(totalXp) else level

    /**
     * Percentage of total XP (across all classes) that is in this class.
     * Used for tier progress calculation - other classes can "bring down" your progress.
     */
    val percentOfTotalXp: Double
        get() {
            val allClassesTotal = ClassModel.getTotalXpAllClasses()
            return if (allClassesTotal > 0) (totalXp / allClassesTotal) * 100.0 else 0.0
        }

    /**
     * Tier progress (0-100) that factors in both XP progress and percentage of total.
     * This is the server's actual tier calculation - other classes can bring this down.
     */
    val tierProgressPercent: Int
        get() = ClassModel.calculateTierProgress(totalXp, percentOfTotalXp)

    // === Session Tracking ===

    /** XP gained during this session (since world join) */
    var sessionXpGained: Double = 0.0
        internal set

    /** Total XP at start of session (for calculating session gains) */
    var sessionStartXp: Double = 0.0
        internal set

    /** Whether session start XP has been recorded */
    var sessionStartRecorded: Boolean = false
        internal set

    // === XP Rate Tracking ===

    /** Recent XP events for rate calculation (last 5 minutes) */
    val xpHistory: ConcurrentLinkedDeque<XpEvent> = ConcurrentLinkedDeque()

    /** Maximum history duration in milliseconds (5 minutes) */
    private val maxHistoryDuration = 5 * 60 * 1000L

    /**
     * Add an XP event to history and update session stats.
     */
    internal fun recordXpEvent(change: Double, newTotalXp: Double) {
        val now = System.currentTimeMillis()

        // Record session start if not yet recorded
        if (!sessionStartRecorded) {
            sessionStartXp = newTotalXp - change
            sessionStartRecorded = true
        }

        // Update session XP gained
        sessionXpGained = newTotalXp - sessionStartXp

        // Only record positive XP gains for rate calculation
        if (change > 0) {
            xpHistory.addLast(XpEvent(now, change, newTotalXp))

            // Prune old entries (older than 5 minutes)
            val cutoff = now - maxHistoryDuration
            while (xpHistory.isNotEmpty() && xpHistory.peekFirst().timestamp < cutoff) {
                xpHistory.removeFirst()
            }
        }
    }

    /**
     * Calculate XP per hour based on recent history.
     * Returns null if not enough data (less than 30 seconds of history).
     */
    fun calculateXpPerHour(): Double? {
        if (xpHistory.size < 2) return null

        val now = System.currentTimeMillis()
        val cutoff = now - maxHistoryDuration

        // Filter to events within the window
        val recentEvents = xpHistory.filter { it.timestamp >= cutoff }
        if (recentEvents.size < 2) return null

        val oldestEvent = recentEvents.first()
        val newestEvent = recentEvents.last()

        val durationMs = newestEvent.timestamp - oldestEvent.timestamp
        if (durationMs < 30_000) return null // Need at least 30 seconds of data

        val totalXpGained = recentEvents.sumOf { it.xpChange }
        val hoursElapsed = durationMs / (1000.0 * 60.0 * 60.0)

        return totalXpGained / hoursElapsed
    }

    /**
     * Estimate time to next level based on current XP rate.
     * Returns time in seconds, or null if cannot estimate.
     */
    fun estimateTimeToLevel(): Long? {
        val xpPerHour = calculateXpPerHour() ?: return null
        if (xpPerHour <= 0) return null

        val xpNeeded = xpForLevel - currentXp
        if (xpNeeded <= 0) return null

        val hoursNeeded = xpNeeded / xpPerHour
        return (hoursNeeded * 3600).toLong()
    }

    /**
     * Reset session tracking data.
     */
    internal fun resetSession() {
        sessionXpGained = 0.0
        sessionStartXp = 0.0
        sessionStartRecorded = false
        xpHistory.clear()
    }
}

/**
 * Model that parses and tracks class/specialization XP data.
 *
 * Data sources:
 * - /class menu: Detailed XP breakdown per class
 * - Actionbar: Real-time total XP and changes for current class
 */
object ClassModel : Model() {

    // === Server XP Formula Replication ===
    // Formula: Math.floor(2 * (25 * lvl^2 + 5*lvl + 200 * 2.45^lvl) - 400)
    // Level 0: 0, Level 1: 640, Level 2: 2221, Level 3: 5962, Level 4: 14855, Level 5: 36219

    /** Maximum skill level */
    const val MAX_LEVEL = 5

    /** Cached XP thresholds for each level (0-5) */
    private val XP_THRESHOLDS: IntArray = IntArray(MAX_LEVEL + 1) { level ->
        if (level == 0) 0
        else floor(
            2.0 * (25.0 * level * level + 5.0 * level + 200.0 * 2.45.pow(level)) - 400.0
        ).toInt()
    }

    /**
     * Get XP threshold required to reach a specific level.
     */
    fun getXpForLevel(level: Int): Int {
        return if (level in 0..MAX_LEVEL) XP_THRESHOLDS[level] else XP_THRESHOLDS[MAX_LEVEL]
    }

    /**
     * Get the maximum XP threshold (level 5).
     */
    fun getMaxXp(): Int = XP_THRESHOLDS[MAX_LEVEL]

    /**
     * Calculate level from total XP.
     */
    fun getLevelFromTotalXp(totalXp: Double): Int {
        for (level in MAX_LEVEL downTo 0) {
            if (totalXp >= XP_THRESHOLDS[level]) return level
        }
        return 0
    }

    /**
     * Calculate total progress percentage (0-100) toward max level.
     * This shows how far you are through the entire progression from 0 to max.
     */
    fun calculateTotalProgress(totalXp: Double): Int {
        val maxXp = XP_THRESHOLDS[MAX_LEVEL]
        if (maxXp <= 0) return 0
        return ((totalXp / maxXp) * 100).toInt().coerceIn(0, 100)
    }

    /**
     * Calculate progress within current level (0-100).
     * This shows how far you are from current level to next level.
     */
    fun calculateLevelProgress(totalXp: Double): Int {
        val currentLevel = getLevelFromTotalXp(totalXp)
        if (currentLevel >= MAX_LEVEL) return 100

        val currentLevelXp = XP_THRESHOLDS[currentLevel]
        val nextLevelXp = XP_THRESHOLDS[currentLevel + 1]
        val xpInLevel = totalXp - currentLevelXp
        val xpNeededForLevel = nextLevelXp - currentLevelXp

        if (xpNeededForLevel <= 0) return 100
        return ((xpInLevel / xpNeededForLevel) * 100).toInt().coerceIn(0, 100)
    }

    // === Percentage Requirements (server configurable, defaults shown) ===
    // These are the % of total XP required to be in a class to reach each level.
    // Default server values - can be adjusted if server uses different config.
    private val PERCENT_REQUIREMENTS = doubleArrayOf(
        0.0,   // Level 0: no requirement
        0.0,   // Level 1: no requirement (default)
        0.0,   // Level 2: no requirement (default)
        0.0,   // Level 3: no requirement (default)
        0.0,   // Level 4: no requirement (default)
        0.0    // Level 5: no requirement (default)
    )

    /**
     * Calculate tier progress (0-100) which factors in both XP progress AND
     * what percentage of total XP is in this class.
     *
     * Formula: XPProgress% × percentageProgress% × 0.01
     *
     * This means if you have lots of XP in other classes, your tier progress
     * in the current class will be lower.
     */
    fun calculateTierProgress(classXp: Double, percentOfTotal: Double): Int {
        val currentLevel = getLevelFromTotalXp(classXp)
        if (currentLevel >= MAX_LEVEL) return 100

        // XP progress within level
        val xpProgress = calculateLevelProgress(classXp)

        // Percentage progress (how much of total XP is in this class vs requirement)
        val percentMin = PERCENT_REQUIREMENTS.getOrElse(currentLevel) { 0.0 }
        val percentMax = PERCENT_REQUIREMENTS.getOrElse(currentLevel + 1) { 0.0 }

        val percentProgress = if (percentMax <= percentMin) {
            100 // No percentage requirement difference, full progress
        } else {
            mapValue(percentOfTotal - percentMin, 0.0, percentMax - percentMin, 0.0, 100.0)
                .toInt().coerceIn(0, 100)
        }

        // Combined tier progress
        return ((xpProgress * percentProgress) / 100).coerceIn(0, 100)
    }

    /**
     * Map a value from one range to another (like Arduino's map function).
     */
    private fun mapValue(value: Double, inMin: Double, inMax: Double, outMin: Double, outMax: Double): Double {
        if (inMax - inMin == 0.0) return outMax
        return (value - inMin) * (outMax - outMin) / (inMax - inMin) + outMin
    }

    /** Map of class name to class info */
    val classes = mutableMapOf<String, ClassInfo>()

    /** Currently active class (from actionbar) */
    var currentClassName: String = ""
        private set

    /**
     * Get the sum of total XP across all tracked classes.
     * Used for calculating percentage of total.
     */
    fun getTotalXpAllClasses(): Double = classes.values.sumOf { it.totalXp }

    /** Player's username from the menu */
    var playerName: String = ""
        private set

    /** Whether we have valid class data */
    var hasData: Boolean = false
        private set

    /** Timestamp of last successful parse */
    var lastUpdateTime: Long = 0
        private set

    // Regex patterns for /class menu
    private val tierProgressPattern = Regex("""You are (\d+)% progressed""")
    private val levelPattern = Regex("""(.+)\(lvl (\d+)\)""")  // "Novice(lvl 0)"
    private val currentXpPattern = Regex("""Current xp: (\d+)""")
    private val xpMissingPattern = Regex("""([\d.]+)xp to level up""")  // XP still needed

    // Regex pattern for actionbar: "42201.0 (+13.0) Guardsman"
    private val actionBarPattern = Regex("""^([\d.]+)\s+\(([+-][\d.]+)\)\s+(.+)$""")

    // Default class names (used when server doesn't provide via handshake)
    private val defaultClasses = setOf(
        "Farmer", "Builder", "Miner", "Healer",
        "Librarian", "Guardsman", "Blacksmith"
    )

    /**
     * Get known class names.
     * Prefers server-defined classes from handshake, falls back to defaults.
     */
    fun getKnownClasses(): Set<String> {
        val serverClasses = ServerFeaturesModel.getAvailableClasses()
        return if (serverClasses.isNotEmpty()) serverClasses.toSet() else defaultClasses
    }

    private var isInClassMenu = false

    /** Data source for class information */
    enum class DataSource {
        NONE,       // No data yet
        PARSED,     // Parsed from actionbar/container
        CHANNEL     // Received via plugin channel
    }

    /** Current data source */
    var dataSource: DataSource = DataSource.NONE
        private set

    override fun reset() {
        // Reset session data for all existing classes before clearing
        classes.values.forEach { it.resetSession() }
        classes.clear()
        currentClassName = ""
        playerName = ""
        hasData = false
        lastUpdateTime = 0
        isInClassMenu = false
        sessionStartTime = 0
        dataSource = DataSource.NONE
    }

    /**
     * Handle actionbar messages to track real-time XP changes.
     * Format: "42201.0 (+13.0) Guardsman"
     */
    @Subscribe
    fun onActionBarMessage(event: ActionBarMessageEvent) {
        val message = event.rawMessage.trim()
        val match = actionBarPattern.matchEntire(message) ?: return

        try {
            val totalXp = match.groupValues[1].toDouble()
            val change = match.groupValues[2].toDouble()
            val className = match.groupValues[3].trim()

            currentClassName = className

            // Get or create class info
            val classInfo = classes.getOrPut(className) { ClassInfo(name = className) }
            classInfo.totalXp = totalXp
            classInfo.lastChange = change

            // Record XP event for rate tracking and session stats
            classInfo.recordXpEvent(change, totalXp)

            // Record session start time on first XP event
            if (sessionStartTime == 0L) {
                sessionStartTime = System.currentTimeMillis()
            }

            // Also update currentXp based on change (for progress bar)
            // This keeps the bar updating in real-time between /class menu opens
            if (change != 0.0 && classInfo.xpForLevel > 0) {
                classInfo.currentXp += change.toInt()

                // Handle level up: if currentXp exceeds xpForLevel, we leveled up
                if (classInfo.currentXp >= classInfo.xpForLevel) {
                    // Leveled up - carry over excess XP (approximation)
                    classInfo.currentXp = classInfo.currentXp - classInfo.xpForLevel
                    classInfo.level += 1
                    // Note: xpForLevel may change at new level, user should open /class to refresh
                }
                // Handle going negative (shouldn't happen but safety check)
                if (classInfo.currentXp < 0) {
                    classInfo.currentXp = 0
                }
            }

            hasData = true
            lastUpdateTime = System.currentTimeMillis()
            // Only set to PARSED if we don't have channel data
            if (dataSource != DataSource.CHANNEL) {
                dataSource = DataSource.PARSED
            }

            CivutilsMod.logger.debug("ClassModel: $className total=$totalXp change=$change currentXp=${classInfo.currentXp}")
        } catch (e: NumberFormatException) {
            CivutilsMod.logger.debug("ClassModel: Failed to parse actionbar: $message")
        }
    }

    @Subscribe
    fun onScreenOpen(event: ScreenOpenEvent) {
        val screen = event.screen
        CivutilsMod.logger.info("ClassModel: Screen opened: ${screen::class.simpleName}")
        if (screen is AbstractContainerScreen<*>) {
            val title = screen.title.string
            CivutilsMod.logger.info("ClassModel: Container screen opened with title: '$title'")
            isInClassMenu = title == "Your Specialization Stats"
            CivutilsMod.logger.info("ClassModel: isInClassMenu = $isInClassMenu")

            if (isInClassMenu) {
                classes.clear()
                CivutilsMod.logger.info("ClassModel: Detected class menu opened, ready to parse items")
            }
        } else {
            isInClassMenu = false
        }
    }

    @Subscribe
    fun onContainerUpdate(event: ContainerUpdateEvent) {
        if (!isInClassMenu) return

        val stack = event.stack
        if (stack.isEmpty) return

        val customName = stack.get(DataComponents.CUSTOM_NAME)
        val displayName = customName?.string ?: stack.hoverName.string
        CivutilsMod.logger.info("ClassModel: Slot ${event.slot} item: '$displayName' (${stack.item})")

        // Player name (emerald in slot 4)
        if (stack.item.toString().contains("emerald") && event.slot == 4) {
            playerName = displayName
            CivutilsMod.logger.debug("ClassModel: Found player name: $playerName")
            return
        }

        // Only process known classes
        if (!getKnownClasses().contains(displayName)) return

        val lore = stack.get(DataComponents.LORE) ?: return
        val itemType = stack.item.toString()

        // Get or create class info
        val classInfo = classes.getOrPut(displayName) { ClassInfo(name = displayName) }

        // Parse lore based on item type
        var xpMissing = 0.0
        for (line in lore.lines) {
            val lineText = line.string

            // Tier progress (white_carpet items)
            tierProgressPattern.find(lineText)?.let { match ->
                classInfo.tierPercent = match.groupValues[1].toIntOrNull() ?: 0
            }

            // Level name and number (composter items): "Novice(lvl 0)"
            levelPattern.find(lineText)?.let { match ->
                classInfo.levelName = match.groupValues[1].trim()
                classInfo.level = match.groupValues[2].toIntOrNull() ?: 0
            }

            // Current XP: "Current xp: 256"
            currentXpPattern.find(lineText)?.let { match ->
                classInfo.currentXp = match.groupValues[1].toIntOrNull() ?: 0
            }

            // XP missing: "380.0xp to level up"
            xpMissingPattern.find(lineText)?.let { match ->
                xpMissing = match.groupValues[1].toDoubleOrNull() ?: 0.0
            }
        }

        // Calculate total XP needed for level (current + missing)
        if (xpMissing > 0) {
            classInfo.xpForLevel = classInfo.currentXp + xpMissing.toInt()
        }

        hasData = true
        lastUpdateTime = System.currentTimeMillis()

        CivutilsMod.logger.debug(
            "ClassModel: ${classInfo.name} - ${classInfo.levelName}(${classInfo.level}) " +
            "${classInfo.currentXp}/${classInfo.xpForLevel}xp (${classInfo.xpPercent}%)"
        )
    }

    /**
     * Handle class XP data received via plugin channel.
     * This is the preferred data source when available.
     */
    @Subscribe
    fun onClassXpChannel(event: ClassXpChannelEvent) {
        CivutilsMod.logger.debug("ClassModel: Received channel event type=${event.type}")

        when (event.type) {
            "full" -> handleFullChannelData(event)
            "partial" -> handlePartialChannelData(event)
            "levelup", "leveldown" -> handleLevelChangeChannel(event)
        }

        // Update current class if provided
        event.currentClass?.let { currentClassName = it }

        hasData = true
        lastUpdateTime = System.currentTimeMillis()
        dataSource = DataSource.CHANNEL
    }

    /**
     * Handle full class data from channel.
     */
    private fun handleFullChannelData(event: ClassXpChannelEvent) {
        val channelClasses = event.classes ?: return

        // Clear existing and populate from channel data
        classes.clear()

        channelClasses.forEach { (name, data) ->
            val classInfo = ClassInfo(name = name).apply {
                data.level?.let { level = it }
                data.levelName?.let { levelName = it }
                totalXp = data.totalXp
                data.currentXp?.let { currentXp = it }
                data.xpForLevel?.let { xpForLevel = it }
            }
            classes[name] = classInfo
        }

        CivutilsMod.logger.info("ClassModel: Full channel data - ${classes.size} classes")
    }

    /**
     * Handle partial class update from channel.
     */
    private fun handlePartialChannelData(event: ClassXpChannelEvent) {
        val className = event.singleClass ?: return
        val data = event.classes?.get(className) ?: return

        val classInfo = classes.getOrPut(className) { ClassInfo(name = className) }
        classInfo.totalXp = data.totalXp
        data.change?.let {
            classInfo.lastChange = it
            classInfo.recordXpEvent(it, data.totalXp)
        }

        // Record session start time on first XP event
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
        }

        CivutilsMod.logger.debug("ClassModel: Partial channel data - $className totalXp=${data.totalXp} change=${data.change}")
    }

    /**
     * Handle level change from channel.
     */
    private fun handleLevelChangeChannel(event: ClassXpChannelEvent) {
        val className = event.singleClass ?: return
        val data = event.classes?.get(className) ?: return

        val classInfo = classes.getOrPut(className) { ClassInfo(name = className) }
        data.level?.let { classInfo.level = it }
        data.levelName?.let { classInfo.levelName = it }
        classInfo.totalXp = data.totalXp

        CivutilsMod.logger.info("ClassModel: Level change - $className is now level ${classInfo.level} (${classInfo.levelName})")
    }

    fun getClass(name: String): ClassInfo? = classes[name]

    fun getClassesByProgress(): List<ClassInfo> =
        classes.values.sortedByDescending { it.currentXp }

    // === Aggregate Session Statistics ===

    /** Total XP gained across all classes this session */
    val totalSessionXpGained: Double
        get() = classes.values.sumOf { it.sessionXpGained }

    /** Time when the session started (first XP event recorded) */
    var sessionStartTime: Long = 0
        private set

    /** Duration of current session in milliseconds */
    val sessionDurationMs: Long
        get() = if (sessionStartTime > 0) System.currentTimeMillis() - sessionStartTime else 0

    /**
     * Get XP rate for current class (XP per hour).
     * Returns null if not enough data.
     */
    fun getCurrentClassXpRate(): Double? {
        val className = currentClassName
        if (className.isEmpty()) return null
        return classes[className]?.calculateXpPerHour()
    }

    /**
     * Get time to next level for current class (in seconds).
     * Returns null if cannot estimate.
     */
    fun getCurrentClassTimeToLevel(): Long? {
        val className = currentClassName
        if (className.isEmpty()) return null
        return classes[className]?.estimateTimeToLevel()
    }

    /**
     * Format a duration in seconds to a human-readable string.
     * Examples: "5m 30s", "1h 23m", "2h 45m"
     */
    fun formatDuration(seconds: Long): String {
        if (seconds < 60) return "${seconds}s"
        val minutes = seconds / 60
        val secs = seconds % 60
        if (minutes < 60) return "${minutes}m ${secs}s"
        val hours = minutes / 60
        val mins = minutes % 60
        return "${hours}h ${mins}m"
    }

    /**
     * Format XP per hour with K suffix for thousands.
     * Examples: "1.2K/h", "500/h", "12.5K/h"
     */
    fun formatXpPerHour(xpPerHour: Double): String {
        return if (xpPerHour >= 1000) {
            String.format("%.1fK/h", xpPerHour / 1000)
        } else {
            String.format("%.0f/h", xpPerHour)
        }
    }
}
