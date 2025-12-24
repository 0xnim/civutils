package xyz.nim.civutils.models

import net.minecraft.component.DataComponentTypes
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.ActionBarMessageEvent
import xyz.nim.civutils.core.event.ContainerUpdateEvent
import xyz.nim.civutils.core.event.ScreenOpenEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.model.Model

/**
 * Data class representing a single class/specialization.
 */
data class ClassInfo(
    val name: String,
    var tierPercent: Int = 0,        // "You are X% progressed through this tier"
    var levelName: String = "",       // e.g., "Novice"
    var level: Int = 0,               // e.g., 0
    var currentXp: Int = 0,           // Current XP in this level
    var xpForLevel: Int = 0,          // Total XP needed for this level (currentXp + missing)
    var totalXp: Double = 0.0,        // Total XP from actionbar
    var lastChange: Double = 0.0,     // Last XP change (+/-) from actionbar
) {
    /** XP progress as percentage (0-100) */
    val xpPercent: Int
        get() = if (xpForLevel > 0) ((currentXp.toDouble() / xpForLevel) * 100).toInt() else 0
}

/**
 * Model that parses and tracks class/specialization XP data.
 *
 * Data sources:
 * - /class menu: Detailed XP breakdown per class
 * - Actionbar: Real-time total XP and changes for current class
 */
object ClassModel : Model() {

    /** Map of class name to class info */
    val classes = mutableMapOf<String, ClassInfo>()

    /** Currently active class (from actionbar) */
    var currentClassName: String = ""
        private set

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

    // Known class names to look for
    private val knownClasses = setOf(
        "Farmer", "Builder", "Miner", "Healer",
        "Librarian", "Guardsman", "Blacksmith"
    )

    private var isInClassMenu = false

    override fun reset() {
        classes.clear()
        currentClassName = ""
        playerName = ""
        hasData = false
        lastUpdateTime = 0
        isInClassMenu = false
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

            CivutilsMod.logger.debug("ClassModel: $className total=$totalXp change=$change currentXp=${classInfo.currentXp}")
        } catch (e: NumberFormatException) {
            CivutilsMod.logger.debug("ClassModel: Failed to parse actionbar: $message")
        }
    }

    @Subscribe
    fun onScreenOpen(event: ScreenOpenEvent) {
        val screen = event.screen
        if (screen is net.minecraft.client.gui.screen.ingame.HandledScreen<*>) {
            val title = screen.title.string
            isInClassMenu = title == "Your Specialization Stats"

            if (isInClassMenu) {
                classes.clear()
                CivutilsMod.logger.debug("ClassModel: Detected class menu opened")
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

        val customName = stack.get(DataComponentTypes.CUSTOM_NAME)
        val displayName = customName?.string ?: stack.name.string

        // Player name (emerald in slot 4)
        if (stack.item.toString().contains("emerald") && event.slot == 4) {
            playerName = displayName
            CivutilsMod.logger.debug("ClassModel: Found player name: $playerName")
            return
        }

        // Only process known classes
        if (!knownClasses.contains(displayName)) return

        val lore = stack.get(DataComponentTypes.LORE) ?: return
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

    fun getClass(name: String): ClassInfo? = classes[name]

    fun getClassesByProgress(): List<ClassInfo> =
        classes.values.sortedByDescending { it.currentXp }
}
