package xyz.nim.civutils.data.handbook

/**
 * Types of relationships between items based on recipe analysis.
 */
enum class ItemRelationType(val displayName: String) {
    /** This item smelts/cooks into another item */
    SMELTS_INTO("Smelts Into"),

    /** This item is used as a crafting ingredient for another item */
    INGREDIENT_FOR("Ingredient For"),

    /** This item is used in brewing another item */
    BREWS_INTO("Brews Into"),

    /** This item is used at the smithing table */
    UPGRADES_INTO("Upgrades Into"),

    /** This item is cut at the stonecutter into another item */
    CUTS_INTO("Cuts Into"),

    /** Generic "used in" for custom/other recipe types */
    USED_IN("Used In")
}

/**
 * A group of items sharing the same relationship type.
 */
data class ItemRelationGroup(
    val type: ItemRelationType,
    val items: List<ItemDefinition>
)

/**
 * Root structure for items.json - the unified item database.
 */
data class ItemsIndex(
    /** Schema version for future migrations */
    val version: Int = 1,

    /** All item definitions */
    val items: List<ItemDefinition> = emptyList(),

    /** Optional category display order overrides */
    val categoryOrder: List<ItemCategory>? = null
) {
    /** Get items by category */
    fun getItemsByCategory(category: ItemCategory): List<ItemDefinition> {
        return items.filter { it.category == category }.sortedBy { it.order }
    }

    /** Get an item by ID */
    fun getItem(id: String): ItemDefinition? {
        return items.find { it.id == id }
    }

    /** Get all items that have NBT filters (custom server items) */
    fun getCustomItems(): List<ItemDefinition> {
        return items.filter { it.isCustomItem }
    }

    /** Search items by name, summary, or tags */
    fun searchItems(query: String): List<ItemDefinition> {
        if (query.isBlank()) return items.sortedBy { it.order }

        val lowerQuery = query.lowercase()
        return items.filter { item ->
            item.name.lowercase().contains(lowerQuery) ||
            (item.summary?.lowercase()?.contains(lowerQuery) == true) ||
            (item.tags?.any { it.lowercase().contains(lowerQuery) } == true) ||
            item.id.lowercase().contains(lowerQuery)
        }.sortedBy { item ->
            // Sort by relevance: exact name match first, then name contains, then others
            when {
                item.name.lowercase() == lowerQuery -> 0
                item.name.lowercase().startsWith(lowerQuery) -> 1
                item.name.lowercase().contains(lowerQuery) -> 2
                else -> 3
            }
        }
    }

    /** Get items that use the given item as an ingredient (from manual usedIn field) */
    fun getItemsUsing(itemId: String): List<ItemDefinition> {
        return items.filter { itemId in (it.usedIn ?: emptyList()) }
    }

    /**
     * Find all items whose recipes use the given item ID as an ingredient.
     * This is computed dynamically from the recipe database.
     * Returns a list of pairs: (ItemDefinition, Recipe) for each matching recipe.
     */
    fun getRecipesUsingItem(itemId: String): List<Pair<ItemDefinition, Recipe>> {
        val results = mutableListOf<Pair<ItemDefinition, Recipe>>()

        for (item in items) {
            val recipes = item.recipes ?: continue
            for (recipe in recipes) {
                val inputs = recipe.getAllInputs()
                // Check if any input matches the item ID (exact match or alternatives)
                val usesItem = inputs.any { slot ->
                    slot.item == itemId ||
                    slot.alternatives?.contains(itemId) == true
                }
                if (usesItem) {
                    results.add(item to recipe)
                }
            }
        }

        return results.sortedBy { it.first.order }
    }

    /** Get all categories that have at least one item */
    fun getActiveCategories(): List<ItemCategory> {
        val order = categoryOrder ?: ItemCategory.entries
        val activeCategories = items.map { it.category }.toSet()
        return order.filter { it in activeCategories }
    }

    /**
     * Get items that use the given item as an ingredient, grouped by relationship type.
     * Returns relationship groups in display order.
     */
    fun getItemRelationships(itemId: String): List<ItemRelationGroup> {
        val relationMap = mutableMapOf<ItemRelationType, MutableSet<ItemDefinition>>()

        for (item in items) {
            val recipes = item.recipes ?: continue
            for (recipe in recipes) {
                val inputs = recipe.getAllInputs()
                val usesItem = inputs.any { slot ->
                    slot.item == itemId || slot.alternatives?.contains(itemId) == true
                }
                if (!usesItem) continue

                // Determine relationship type based on recipe type
                val relationType = when (recipe.type) {
                    RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING, RecipeType.CAMPFIRE ->
                        ItemRelationType.SMELTS_INTO
                    RecipeType.CRAFTING_SHAPED, RecipeType.CRAFTING_SHAPELESS, RecipeType.CRAFTING_2X2 ->
                        ItemRelationType.INGREDIENT_FOR
                    RecipeType.BREWING ->
                        ItemRelationType.BREWS_INTO
                    RecipeType.SMITHING ->
                        ItemRelationType.UPGRADES_INTO
                    RecipeType.STONECUTTING ->
                        ItemRelationType.CUTS_INTO
                    RecipeType.CARTOGRAPHY, RecipeType.CUSTOM ->
                        ItemRelationType.USED_IN
                }

                relationMap.getOrPut(relationType) { mutableSetOf() }.add(item)
            }
        }

        // Return in enum declaration order (most common relationships first)
        return ItemRelationType.entries
            .filter { it in relationMap }
            .map { type -> ItemRelationGroup(type, relationMap[type]!!.sortedBy { it.order }) }
    }
}
