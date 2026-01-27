package xyz.nim.civutils.utils

import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.ItemStack
import xyz.nim.civutils.data.handbook.CustomItemDefinition
import xyz.nim.civutils.data.handbook.ItemFilters
import xyz.nim.civutils.models.HandbookModel

/**
 * Utility for matching ItemStacks against custom item definitions.
 * Used to find handbook pages for custom server items identified by NBT/components.
 */
object ItemMatcher {

    private var customItems: List<CustomItemDefinition> = emptyList()
    private var itemsById: Map<String, CustomItemDefinition> = emptyMap()

    /**
     * Load custom item definitions.
     * Called by HandbookModel when loading content.
     */
    fun loadDefinitions(definitions: List<CustomItemDefinition>) {
        customItems = definitions
        itemsById = definitions.associateBy { it.id }
    }

    /**
     * Get a custom item definition by its ID.
     */
    fun getDefinition(id: String): CustomItemDefinition? = itemsById[id]

    /**
     * Find a custom item definition that matches the given ItemStack.
     * Returns the first matching definition, or null if no match.
     */
    fun matchItem(stack: ItemStack): CustomItemDefinition? {
        if (stack.isEmpty) return null

        for (def in customItems) {
            if (matchesFilters(stack, def.filters)) {
                return def
            }
        }
        return null
    }

    /**
     * Check if an ItemStack matches all the specified filters.
     * Uses AND logic - all non-null filters must match.
     */
    private fun matchesFilters(stack: ItemStack, filters: ItemFilters): Boolean {
        // Check base item
        if (filters.baseItem != null) {
            val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()
            if (itemId != filters.baseItem) return false
        }

        // Check custom name (exact match)
        val customName = stack.get(DataComponents.CUSTOM_NAME)?.string
        if (filters.customName != null) {
            if (customName != filters.customName) return false
        }

        // Check custom name contains (case-insensitive)
        if (filters.customNameContains != null) {
            if (customName == null || !customName.contains(filters.customNameContains, ignoreCase = true)) {
                return false
            }
        }

        // Check lore
        val loreComponent = stack.get(DataComponents.LORE)
        val loreLines = loreComponent?.lines?.map { it.string } ?: emptyList()

        // Check lore contains (all must be present somewhere in lore, case-insensitive)
        if (filters.loreContains.isNotEmpty()) {
            val loreText = loreLines.joinToString(" ").lowercase()
            for (required in filters.loreContains) {
                if (!loreText.contains(required.lowercase())) {
                    return false
                }
            }
        }

        // Check lore exact match
        if (filters.loreExact.isNotEmpty()) {
            if (loreLines != filters.loreExact) return false
        }

        // Check custom model data
        // In 1.21.4+, CustomModelData has floats, flags, strings, colors lists
        // The legacy integer value is typically stored as the first float
        if (filters.customModelData != null) {
            val modelData = stack.get(DataComponents.CUSTOM_MODEL_DATA)
            val floats = modelData?.floats() ?: emptyList()
            val modelValue = floats.firstOrNull()?.toInt()
            if (modelValue != filters.customModelData) return false
        }

        return true
    }

    /**
     * Get the handbook page ID for an ItemStack.
     * First checks custom item definitions (from items.json or custom-items.json),
     * then falls back to vanilla item ID matching.
     *
     * @return The page/item ID if a matching entry exists, null otherwise
     */
    fun getPageIdForItem(stack: ItemStack): String? {
        if (stack.isEmpty) return null

        // First try custom item match (checks items with NBT filters)
        matchItem(stack)?.let { return it.pageId }

        // Fall back to vanilla item ID matching
        val itemId = BuiltInRegistries.ITEM.getKey(stack.item).toString()

        // Check items database for displayItem match
        // Skip items with NBT filters - those should only be matched by their filters above,
        // not by displayItem (which is used for rendering, not identification)
        val itemMatch = HandbookModel.getAllItems().find {
            it.displayItem == itemId && !it.isCustomItem
        }
        if (itemMatch != null) return itemMatch.id

        // Check legacy page itemId match
        return HandbookModel.getPages().find { it.itemId == itemId }?.id
    }

    /**
     * Check if there are any custom item definitions loaded.
     */
    fun hasDefinitions(): Boolean = customItems.isNotEmpty()

    /**
     * Get all custom item definitions.
     */
    fun getDefinitions(): List<CustomItemDefinition> = customItems
}
