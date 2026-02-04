package xyz.nim.civutils.data.handbook

/**
 * Item categories for organization in the handbook.
 */
enum class ItemCategory(val displayName: String, val icon: String) {
    MATERIALS("Materials", "minecraft:iron_ingot"),
    TOOLS("Tools", "minecraft:iron_pickaxe"),
    ARMOR("Armor", "minecraft:iron_chestplate"),
    WEAPONS("Weapons", "minecraft:iron_sword"),
    FOOD("Food & Healing", "minecraft:bread"),
    BREWING("Brewing", "minecraft:brewing_stand"),
    MISC("Miscellaneous", "minecraft:chest")
}

/**
 * Main item definition - the central entry for each item in items.json.
 * Combines item metadata, recipes, and NBT matching filters in one structure.
 */
data class ItemDefinition(
    /** Unique item identifier (e.g., "iron_plate", "bandage") */
    val id: String,

    /** Display name */
    val name: String,

    /** Brief summary for search results and tooltips */
    val summary: String? = "",

    /** Markdown description content (rendered in the handbook) */
    val description: String? = "",

    /** Item category for grouping */
    val category: ItemCategory = ItemCategory.MISC,

    /** Tags for search and filtering (nullable due to Gson) */
    val tags: List<String>? = emptyList(),

    /** Display order within category */
    val order: Int = 0,

    /** Vanilla item ID for display rendering (e.g., "minecraft:iron_ingot") */
    val displayItem: String? = null,

    /** Custom texture path relative to civutils textures (e.g., "item/copper_pickaxe_head") */
    val customTexture: String? = null,

    /** NBT filters for matching custom server items (reuses existing ItemFilters) */
    val filters: ItemFilters? = null,

    /** List of recipes that produce this item (nullable due to Gson) */
    val recipes: List<Recipe>? = emptyList(),

    /** Items that drop when this block/item is broken (e.g., ore drops gems) */
    val drops: List<RecipeSlot>? = null,

    /** List of item IDs that use this item as an ingredient (nullable due to Gson) */
    val usedIn: List<String>? = emptyList(),

    /** Related item/page IDs for cross-referencing (nullable due to Gson) */
    val related: List<String>? = emptyList(),

    /** Required class and level to craft (e.g., "blacksmith:2") */
    val requiredClass: String? = null,

    /** Required class and level to interact with this block (e.g., "healer:4") */
    val interactionRequirement: String? = null,

    /** Required class and level to mine this block (e.g., "miner:3") */
    val miningRequirement: String? = null,

    /** Additional metadata for server-specific properties (nullable due to Gson) */
    val metadata: Map<String, String>? = null
) {
    /** Check if this is a custom server item (has NBT filters) */
    val isCustomItem: Boolean get() = filters != null

    /** Get the item ID for display rendering */
    val renderItemId: String get() = displayItem ?: filters?.baseItem ?: "minecraft:barrier"

    /** Parse required class into name and level */
    val requiredClassInfo: Pair<String, Int>?
        get() = requiredClass?.split(":")?.let { parts ->
            if (parts.size == 2) {
                parts[0] to (parts[1].toIntOrNull() ?: 0)
            } else null
        }

    /** Parse interaction requirement into name and level */
    val interactionRequirementInfo: Pair<String, Int>?
        get() = interactionRequirement?.split(":")?.let { parts ->
            if (parts.size == 2) {
                parts[0] to (parts[1].toIntOrNull() ?: 0)
            } else null
        }

    /** Parse mining requirement into name and level */
    val miningRequirementInfo: Pair<String, Int>?
        get() = miningRequirement?.split(":")?.let { parts ->
            if (parts.size == 2) {
                parts[0] to (parts[1].toIntOrNull() ?: 0)
            } else null
        }
}
