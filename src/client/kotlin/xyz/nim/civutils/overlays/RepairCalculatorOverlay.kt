package xyz.nim.civutils.overlays

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.booleanConfig
import xyz.nim.civutils.core.config.intConfig
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.core.overlay.Overlay
import xyz.nim.civutils.core.overlay.OverlayPosition
import xyz.nim.civutils.core.overlay.OverlaySize
import xyz.nim.civutils.models.ClassModel
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.BooleanConfig
import xyz.nim.lib.config.options.IntegerConfig
import kotlin.math.ceil
import kotlin.math.min

/**
 * Data class for repair calculation results.
 */
data class RepairInfo(
    val itemName: String,
    val currentDurability: Int,
    val maxDurability: Int,
    val damageAmount: Int,
    val repairsNeeded: Int,
    val hungerCost: Int,
    val xpLevelsCost: Int,
    val blacksmithXpGained: Int,
    val repairPerAction: Int,
    val blacksmithLevel: Int
)

/**
 * Overlay that shows repair costs when holding damaged items.
 *
 * Server repair formula:
 * - Cost per repair: 1 hunger + 1 XP level
 * - Repair amount: 3 + (blacksmithLevel - 1) durability, capped at 10
 * - XP gain: 1 Blacksmith XP per repair
 * - Requires: Blacksmith level 1+, sneak + right-click anvil
 */
class RepairCalculatorOverlay : Overlay(
    position = OverlayPosition.bottomLeft(offsetX = 5, offsetY = -60),
    size = OverlaySize(width = 150, height = 100)
) {
    override val displayName = "Repair Calculator"

    private val mc: Minecraft get() = Minecraft.getInstance()

    // === Config Options ===

    /**
     * Blacksmith level override. If 0, uses detected level from ClassModel.
     */
    val blacksmithLevelOverride: IntegerConfig = intConfig(
        name = "blacksmithLevelOverride",
        default = 0,
        min = 0,
        max = 5,
        displayName = "Blacksmith Level Override",
        comment = "Override blacksmith level (0 = auto-detect from /class)"
    )

    /**
     * Show overlay only when looking at an anvil.
     */
    val requireAnvilLook: BooleanConfig = booleanConfig(
        name = "requireAnvilLook",
        default = false,
        displayName = "Require Looking at Anvil",
        comment = "Only show overlay when looking at an anvil"
    )

    /**
     * Show detailed breakdown in overlay.
     */
    val showDetailedInfo: BooleanConfig = booleanConfig(
        name = "showDetailedInfo",
        default = true,
        displayName = "Show Detailed Info",
        comment = "Show detailed repair breakdown"
    )

    init {
        blacksmithLevelOverride.onValueChanged { onConfigUpdate(blacksmithLevelOverride) }
        requireAnvilLook.onValueChanged { onConfigUpdate(requireAnvilLook) }
        showDetailedInfo.onValueChanged { onConfigUpdate(showDetailedInfo) }
    }

    override fun getConfigs(): List<ConfigOption<*>> = listOf(
        enabled, blacksmithLevelOverride, requireAnvilLook, showDetailedInfo
    )

    override fun onConfigUpdate(config: ConfigOption<*>) {
        CivutilsMod.configManager.markDirty()
    }

    // === Repair Calculation ===

    /**
     * Get the effective blacksmith level.
     * Uses override if set, otherwise tries to get from ClassModel.
     * Returns 0 if no blacksmith data is available.
     */
    private fun getBlacksmithLevel(): Int {
        if (blacksmithLevelOverride.value > 0) {
            return blacksmithLevelOverride.value
        }

        // Try to get from ClassModel
        val blacksmithInfo = ClassModel.getClass("Blacksmith")
        return blacksmithInfo?.calculatedLevel ?: 0
    }

    /**
     * Calculate repair amount per action based on blacksmith level.
     * Formula: 3 + (level - 1), capped at 10
     */
    private fun getRepairPerAction(blacksmithLevel: Int): Int {
        val repair = 3 + (blacksmithLevel - 1)
        return min(repair, 10)
    }

    /**
     * Calculate full repair info for an item.
     * Returns null if blacksmith level is 0 (repairs require level 1+).
     */
    private fun calculateRepairInfo(stack: ItemStack): RepairInfo? {
        if (stack.isEmpty) return null
        if (!stack.isDamageableItem) return null

        val maxDurability = stack.maxDamage
        val currentDamage = stack.damageValue
        if (currentDamage <= 0) return null

        val blacksmithLevel = getBlacksmithLevel()
        // Repairs require Blacksmith level 1+
        if (blacksmithLevel < 1) return null

        val currentDurability = maxDurability - currentDamage
        val repairPerAction = getRepairPerAction(blacksmithLevel)

        // Calculate number of repairs needed
        val repairsNeeded = ceil(currentDamage.toDouble() / repairPerAction).toInt()

        return RepairInfo(
            itemName = stack.hoverName.string,
            currentDurability = currentDurability,
            maxDurability = maxDurability,
            damageAmount = currentDamage,
            repairsNeeded = repairsNeeded,
            hungerCost = repairsNeeded,
            xpLevelsCost = repairsNeeded,
            blacksmithXpGained = repairsNeeded,
            repairPerAction = repairPerAction,
            blacksmithLevel = blacksmithLevel
        )
    }

    /**
     * Check if player is looking at an anvil.
     */
    private fun isLookingAtAnvil(): Boolean {
        val hitResult = mc.hitResult ?: return false
        if (hitResult.type != HitResult.Type.BLOCK) return false

        val blockHitResult = hitResult as BlockHitResult
        val blockState = mc.level?.getBlockState(blockHitResult.blockPos) ?: return false
        val blockName = blockState.block.name.string.lowercase()

        return blockName.contains("anvil")
    }

    /**
     * Get repair info for currently held item.
     */
    private fun getCurrentRepairInfo(): RepairInfo? {
        val player = mc.player ?: return null

        // Check anvil requirement
        if (requireAnvilLook.value && !isLookingAtAnvil()) return null

        // Get held item
        val mainHand = player.mainHandItem
        val offHand = player.offhandItem

        return calculateRepairInfo(mainHand) ?: calculateRepairInfo(offHand)
    }

    // === Rendering ===

    override fun shouldRender(): Boolean {
        if (!super.shouldRender()) return false
        return getCurrentRepairInfo() != null
    }

    override fun render(guiGraphics: GuiGraphics, tickDelta: Float) {
        val info = getCurrentRepairInfo() ?: return
        renderRepairInfo(guiGraphics, info)
    }

    private fun renderRepairInfo(guiGraphics: GuiGraphics, info: RepairInfo) {
        val font = mc.font

        // Build lines
        val lines = buildDisplayLines(info)
        if (lines.isEmpty()) return

        // Calculate dimensions
        val lineHeight = font.lineHeight + 2
        val padding = 6

        // Calculate max width by measuring each line (font.width handles § codes)
        var maxWidth = 0
        for (line in lines) {
            // Strip formatting codes for width calculation: §X where X is any char
            val stripped = stripFormatting(line)
            val width = font.width(stripped)
            if (width > maxWidth) maxWidth = width
        }

        // Set size with padding
        size.width = maxWidth + (padding * 2)
        size.height = (lines.size * lineHeight) + (padding * 2) - 2

        val x = getRenderX()
        val y = getRenderY()

        // Background with semi-transparent black
        val bgColor = (0xCC shl 24) or 0x000000
        guiGraphics.fill(x, y, x + size.width, y + size.height, bgColor)

        // Border
        val borderColor = (0xFF shl 24) or 0x444444
        guiGraphics.fill(x, y, x + size.width, y + 1, borderColor)
        guiGraphics.fill(x, y + size.height - 1, x + size.width, y + size.height, borderColor)
        guiGraphics.fill(x, y, x + 1, y + size.height, borderColor)
        guiGraphics.fill(x + size.width - 1, y, x + size.width, y + size.height, borderColor)

        // Render lines - use white color with full alpha
        val textColor = (0xFF shl 24) or 0xFFFFFF
        var currentY = y + padding
        for (line in lines) {
            guiGraphics.drawString(font, line, x + padding, currentY, textColor, true)
            currentY += lineHeight
        }
    }

    /**
     * Strip Minecraft formatting codes (§X) from a string.
     */
    private fun stripFormatting(text: String): String {
        val result = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '§' && i + 1 < text.length) {
                // Skip the § and the following character
                i += 2
            } else {
                result.append(c)
                i++
            }
        }
        return result.toString()
    }

    override fun renderPreview(guiGraphics: GuiGraphics, tickDelta: Float) {
        // Preview data
        val previewInfo = RepairInfo(
            itemName = "Diamond Pickaxe",
            currentDurability = 1234,
            maxDurability = 1561,
            damageAmount = 327,
            repairsNeeded = 47,
            hungerCost = 47,
            xpLevelsCost = 47,
            blacksmithXpGained = 47,
            repairPerAction = 7,
            blacksmithLevel = 5
        )

        renderRepairInfo(guiGraphics, previewInfo)
    }

    /**
     * Build the display lines for repair info.
     */
    private fun buildDisplayLines(info: RepairInfo): List<String> {
        val lines = mutableListOf<String>()

        lines.add("§e${info.itemName}")
        lines.add("§7Durability: §f${info.currentDurability}§7/§f${info.maxDurability}")

        if (showDetailedInfo.value) {
            lines.add("")
            lines.add("§7Repairs needed: §f${info.repairsNeeded}")
            lines.add("§7Repair per action: §f${info.repairPerAction} §8(Lvl ${info.blacksmithLevel})")
            lines.add("")
            lines.add("§6Hunger cost: §f${info.hungerCost}")
            lines.add("§aXP levels cost: §f${info.xpLevelsCost}")
            lines.add("§dBlacksmith XP: §f+${info.blacksmithXpGained}")
        } else {
            lines.add("§7Repairs: §f${info.repairsNeeded} §8(${info.hungerCost} hunger, ${info.xpLevelsCost} lvls)")
        }

        return lines
    }
}
