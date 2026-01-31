package xyz.nim.civutils.utils

import net.minecraft.world.item.ItemStack
import xyz.nim.lib.mc121.compat.McItems

/**
 * Utility functions for parsing item IDs and creating ItemStacks.
 */
object ItemUtils {

    /**
     * Parse an item ID string into an ItemStack.
     * @param id Item ID like "minecraft:iron_ingot" or "iron_ingot" (assumes minecraft namespace)
     * @return ItemStack or null if item not found
     */
    fun parseItemId(id: String): ItemStack? {
        return McItems.parseItemId(id)
    }

    /**
     * Parse an item specification with optional count.
     * @param spec Item spec like "minecraft:iron_ore|64" or "minecraft:iron_ore"
     * @return Pair of (ItemStack, count) or null if item not found
     */
    fun parseItemWithCount(spec: String): Pair<ItemStack, Int>? {
        val parts = spec.split("|", limit = 2)
        val stack = parseItemId(parts[0].trim()) ?: return null
        val count = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1
        return stack to count
    }

    /**
     * Create an ItemStack with a specific count.
     * @param id Item ID
     * @param count Stack count
     * @return ItemStack with count set, or null if item not found
     */
    fun createStack(id: String, count: Int = 1): ItemStack? {
        val stack = parseItemId(id) ?: return null
        stack.count = count
        return stack
    }

    /**
     * Check if an item ID is valid (exists in registry).
     */
    fun isValidItemId(id: String): Boolean {
        return McItems.isValidItemId(id)
    }
}
