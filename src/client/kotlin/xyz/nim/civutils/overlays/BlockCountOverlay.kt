package xyz.nim.civutils.overlays

import net.minecraft.world.item.BlockItem
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.booleanConfig
import xyz.nim.civutils.core.config.onChange
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.core.overlay.OverlayPosition
import xyz.nim.civutils.core.overlay.OverlaySize
import xyz.nim.civutils.core.overlay.TextOverlay
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.BooleanConfig

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
    val onlyBlocks: BooleanConfig = booleanConfig(
        name = "onlyBlocks",
        default = true,
        displayName = "Only Blocks",
        comment = "Only show for block items"
    ).onChange { onConfigUpdate(onlyBlocks) }

    /**
     * Show the item name alongside the count.
     */
    val showItemName: BooleanConfig = booleanConfig(
        name = "showItemName",
        default = false,
        displayName = "Show Item Name",
        comment = "Show item name with count"
    ).onChange { onConfigUpdate(showItemName) }

    override fun getConfigs(): List<ConfigOption<*>> = listOf(
        enabled, textShadow, textColor, onlyBlocks, showItemName
    )

    override fun onConfigUpdate(config: ConfigOption<*>) {
        CivutilsMod.configManager.markDirty()
    }

    override fun getTemplate(): String {
        val player = mc.player ?: return ""
        val heldStack = player.mainHandItem

        if (heldStack.isEmpty) return ""

        // If onlyBlocks is enabled, only show for block items
        if (onlyBlocks.value && heldStack.item !is BlockItem) return ""

        val heldItem = heldStack.item
        var totalCount = 0

        // Count all matching items in the inventory
        val inventory = player.inventory
        for (i in 0 until inventory.containerSize) {
            val stack = inventory.getItem(i)
            if (!stack.isEmpty && stack.item == heldItem) {
                totalCount += stack.count
            }
        }

        return if (showItemName.value) {
            val itemName = heldStack.hoverName.string
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
