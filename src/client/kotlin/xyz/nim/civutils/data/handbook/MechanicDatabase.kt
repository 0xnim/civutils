package xyz.nim.civutils.data.handbook

/**
 * Represents a game mechanic that can be unlocked at different class levels.
 * Mechanics are abilities or interactions that aren't tied to specific items.
 */
data class MechanicDefinition(
    /** Unique identifier for the mechanic (e.g., "force-feed") */
    val id: String,

    /** Display name for the mechanic */
    val name: String,

    /** Brief description of what this mechanic does */
    val summary: String,

    /** Page ID to link to for more details (optional) */
    val pageId: String? = null,

    /** List of class unlocks for this mechanic */
    val classUnlocks: List<MechanicUnlock> = emptyList()
)

/**
 * Represents a class/level that unlocks a mechanic with optional class-specific effects.
 */
data class MechanicUnlock(
    /** Class name (e.g., "guardsman", "healer") */
    val className: String,

    /** Level required to unlock (1-5) */
    val level: Int,

    /** Optional class-specific effect description (e.g., "Full food value" vs "Half food value") */
    val description: String? = null
)

/**
 * Index of all mechanics in the handbook.
 */
data class MechanicsIndex(
    /** All mechanic definitions */
    val mechanics: List<MechanicDefinition> = emptyList()
) {
    /** Lazy-built index for quick lookup by class and level */
    private val classLevelIndex: Map<String, List<MechanicDefinition>> by lazy {
        val index = mutableMapOf<String, MutableList<MechanicDefinition>>()
        for (mechanic in mechanics) {
            for (unlock in mechanic.classUnlocks) {
                val key = "${unlock.className}:${unlock.level}"
                index.getOrPut(key) { mutableListOf() }.add(mechanic)
            }
        }
        index.mapValues { it.value.toList() }
    }

    /** Get mechanics unlocked at a specific class level */
    fun getMechanicsByClassLevel(className: String, level: Int): List<MechanicDefinition> {
        val key = "$className:$level"
        return classLevelIndex[key] ?: emptyList()
    }

    /** Get a mechanic by ID */
    fun getMechanic(id: String): MechanicDefinition? {
        return mechanics.find { it.id == id }
    }
}
