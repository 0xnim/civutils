package xyz.nim.civutils.overlays

import net.minecraft.item.BlockItem
import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.config.Persisted
import xyz.nim.civutils.core.overlay.OverlayPosition
import xyz.nim.civutils.core.overlay.OverlaySize
import xyz.nim.civutils.core.overlay.TextOverlay

/**
 * Builder helper: Shows the total count of the currently held block type
 * across the entire inventory.
 *
 * Useful for builders to see exactly how many blocks they have available.
 */
class BlockCountOverlay : TextOverlay(
    position = OverlayPosition.middleLeft(offsetX = 5, offsetY = 0),
    size = OverlaySize(width = 100, height = 20)
) {
    override val displayName = "Block Count"

    /**
     * Only show for block items (not tools, food, etc).
     */
    @Persisted
    val onlyBlocks = Config(defaultValue = true)

    /**
     * Show the item name alongside the count.
     */
    @Persisted
    val showItemName = Config(defaultValue = false)

    override fun getTemplate(): String {
        val player = mc.player ?: return ""
        val heldStack = player.mainHandStack

        if (heldStack.isEmpty) return ""

        // If onlyBlocks is enabled, only show for block items
        if (onlyBlocks.value && heldStack.item !is BlockItem) return ""

        val heldItem = heldStack.item
        var totalCount = 0

        // Count all matching items in the inventory
        val inventory = player.inventory
        for (i in 0 until inventory.size()) {
            val stack = inventory.getStack(i)
            if (!stack.isEmpty && stack.item == heldItem) {
                totalCount += stack.count
            }
        }

        return if (showItemName.value) {
            val itemName = heldStack.name.string
            "§f$totalCount §7$itemName"
        } else {
            "§6Total: §f$totalCount"
        }
    }

    override fun getPreviewTemplate(): String {
        return if (showItemName.value) {
            "§f64 §7Stone"
        } else {
            "§6Total: §f64"
        }
    }
}
