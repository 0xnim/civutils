package xyz.nim.civutils.data.handbook

/**
 * Represents filters for matching an ItemStack against a custom item definition.
 * Multiple filters use AND logic - all specified filters must match.
 */
data class ItemFilters(
    /** Base item registry ID (e.g., "minecraft:bread") */
    val baseItem: String? = null,
    /** Exact custom name match */
    val customName: String? = null,
    /** Custom name contains this text (case-insensitive) */
    val customNameContains: String? = null,
    /** Custom name must NOT contain this text (case-insensitive) */
    val customNameExcludes: String? = null,
    /** Lore must contain all of these texts (case-insensitive, any line) */
    val loreContains: List<String> = emptyList(),
    /** Lore must exactly match these lines */
    val loreExact: List<String> = emptyList(),
    /** Custom model data value must match (legacy integer format) */
    val customModelData: Int? = null,
    /** Custom model data string must match (1.21+ format, checks strings list) */
    val customModelDataString: String? = null
)

/**
 * Represents a custom item definition that maps to a handbook page.
 * Custom items are identified by NBT/component filters rather than simple item IDs.
 */
data class CustomItemDefinition(
    /** Unique identifier for this custom item (e.g., "blessed_bread") */
    val id: String,
    /** Handbook page ID to link to */
    val pageId: String,
    /** Filters to match ItemStacks against */
    val filters: ItemFilters
)

/**
 * Index of all custom item definitions.
 * Loaded from custom-items.json in the handbook resource folder.
 */
data class CustomItemsIndex(
    val version: Int = 1,
    val items: List<CustomItemDefinition> = emptyList()
)
