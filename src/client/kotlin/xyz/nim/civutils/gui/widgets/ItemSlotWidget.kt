package xyz.nim.civutils.gui.widgets

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.CustomModelData
import net.minecraft.world.item.component.ItemLore
import xyz.nim.civutils.data.handbook.CustomItemDefinition
import xyz.nim.civutils.data.handbook.CustomTextureRegistry
import xyz.nim.civutils.data.handbook.ItemDefinition
import xyz.nim.civutils.utils.ItemUtils
import xyz.nim.lib.ui.NlibTheme

/**
 * Renders an item like an inventory slot with hover tooltip support.
 * Use for inline item references and recipe displays.
 *
 * Supports both vanilla items (by item ID) and custom items (by CustomItemDefinition).
 * Can also render custom textures for custom server items.
 */
class ItemSlotWidget(
    private val itemId: String,
    private val count: Int = 1,
    private val size: SlotSize = SlotSize.NORMAL,
    private val customItemDef: CustomItemDefinition? = null,
    private val itemDefinition: ItemDefinition? = null,
    /** ID used for navigation/click handling - may differ from display itemId */
    private val navigationId: String? = null,
    /** Custom texture path relative to civutils (e.g., "textures/item/copper_pickaxe_head.png") */
    private val customTexture: String? = null
) {
    enum class SlotSize(val pixels: Int, val itemScale: Float) {
        SMALL(14, 0.75f),   // For inline text
        NORMAL(18, 1.0f),   // Standard inventory slot
        LARGE(32, 2.0f)     // For page headers
    }

    private var lastX: Int = 0
    private var lastY: Int = 0
    private var cachedStack: ItemStack? = null
    private var stackResolved = false

    /**
     * Get the ItemStack, resolving from ID if needed.
     * If an itemDefinition or customItemDef is provided, creates a stack with the appropriate components.
     * If a customTexture is specified, uses a barrier item with custom model data.
     */
    private fun getStack(): ItemStack {
        if (!stackResolved) {
            cachedStack = when {
                // Check for custom texture first - use barrier with custom model data
                customTexture != null -> createCustomTextureStack(customTexture)
                itemDefinition?.customTexture != null -> createCustomTextureStack(itemDefinition.customTexture!!)
                itemDefinition != null -> createItemDefinitionStack(itemDefinition)
                customItemDef != null -> createCustomItemStack(customItemDef)
                else -> ItemUtils.createStack(itemId, count)
            }
            stackResolved = true
        }
        return cachedStack ?: ItemStack.EMPTY
    }

    /**
     * Create an ItemStack using a barrier item with custom model data for custom textures.
     */
    private fun createCustomTextureStack(texturePath: String): ItemStack? {
        val modelData = CustomTextureRegistry.getModelData(texturePath) ?: return null
        val stack = ItemUtils.createStack("minecraft:barrier", count) ?: return null

        // Set custom model data to trigger our model override
        stack.set(
            DataComponents.CUSTOM_MODEL_DATA,
            CustomModelData(
                listOf(modelData.toFloat()),
                emptyList(),
                emptyList(),
                emptyList()
            )
        )

        // Set custom name from itemDefinition if available
        itemDefinition?.let { def ->
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(def.name))
        }

        return stack
    }

    /**
     * Create an ItemStack from an ItemDefinition with the item's display name.
     */
    private fun createItemDefinitionStack(def: ItemDefinition): ItemStack? {
        val baseItemId = def.displayItem ?: def.filters?.baseItem ?: return null
        val stack = ItemUtils.createStack(baseItemId, count) ?: return null

        // Apply the item's name as custom name
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(def.name))

        // Apply custom model data if defined in filters
        def.filters?.customModelData?.let { modelData ->
            stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                CustomModelData(
                    listOf(modelData.toFloat()),
                    emptyList(),
                    emptyList(),
                    emptyList()
                )
            )
        }

        return stack
    }

    /**
     * Create an ItemStack from a CustomItemDefinition with appropriate components.
     * This allows custom items to render with their custom model data textures.
     */
    private fun createCustomItemStack(def: CustomItemDefinition): ItemStack? {
        val baseItemId = def.filters.baseItem ?: return null
        val stack = ItemUtils.createStack(baseItemId, count) ?: return null

        // Apply custom name if defined
        def.filters.customName?.let { name ->
            stack.set(DataComponents.CUSTOM_NAME, Component.literal(name))
        }

        // Apply custom model data if defined (this is what drives custom textures)
        // In 1.21.4+, CustomModelData takes lists of: floats, flags, strings, colors
        def.filters.customModelData?.let { modelData ->
            stack.set(
                DataComponents.CUSTOM_MODEL_DATA,
                CustomModelData(
                    listOf(modelData.toFloat()),  // floats - used for range_dispatch model selection
                    emptyList(),                   // flags
                    emptyList(),                   // strings
                    emptyList()                    // colors
                )
            )
        }

        // Apply lore if defined
        if (def.filters.loreExact.isNotEmpty()) {
            val loreComponents = def.filters.loreExact.map { Component.literal(it) }
            stack.set(DataComponents.LORE, ItemLore(loreComponents))
        }

        return stack
    }

    /**
     * Render the item slot at the given position.
     * @return The width consumed
     */
    fun render(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int,
        renderBackground: Boolean = true
    ): Int {
        lastX = x
        lastY = y
        val stack = getStack()
        val slotSize = size.pixels

        // Background
        if (renderBackground) {
            val bgColor = if (isHovered(mouseX, mouseY)) 0x60FFFFFF else 0x40000000
            guiGraphics.fill(x, y, x + slotSize, y + slotSize, bgColor)
            guiGraphics.renderOutline(x, y, slotSize, slotSize, 0x40FFFFFF)
        }

        if (stack.isEmpty) {
            // Render placeholder for unknown items
            renderMissingItem(guiGraphics, x, y)
        } else {
            val mc = Minecraft.getInstance()
            val itemX = x + (slotSize - 16) / 2
            val itemY = y + (slotSize - 16) / 2

            // Render the item (custom textures are handled via barrier + custom model data)
            guiGraphics.renderItem(stack, itemX, itemY)

            // Render count decoration
            if (count > 1) {
                guiGraphics.renderItemDecorations(mc.font, stack, itemX, itemY)
            }
        }

        return slotSize
    }

    /**
     * Render a placeholder for missing/unknown items.
     */
    private fun renderMissingItem(guiGraphics: GuiGraphics, x: Int, y: Int) {
        val mc = Minecraft.getInstance()
        val slotSize = size.pixels
        val centerX = x + slotSize / 2
        val centerY = y + slotSize / 2

        // Draw a question mark
        guiGraphics.drawString(
            mc.font,
            "?",
            centerX - mc.font.width("?") / 2,
            centerY - mc.font.lineHeight / 2,
            NlibTheme.TEXT_SECONDARY,
            false
        )
    }


    /**
     * Check if the slot is currently hovered.
     */
    fun isHovered(mouseX: Int, mouseY: Int): Boolean {
        val slotSize = size.pixels
        return mouseX >= lastX && mouseX < lastX + slotSize &&
                mouseY >= lastY && mouseY < lastY + slotSize
    }

    /**
     * Render the item tooltip. Call this in the overlay pass (after content).
     */
    fun renderTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (!isHovered(mouseX, mouseY)) return

        val stack = getStack()
        val mc = Minecraft.getInstance()

        if (stack.isEmpty) {
            // Show item ID for missing items using custom tooltip
            guiGraphics.drawTooltip(listOf("Unknown item:", itemId), mouseX, mouseY)
        } else {
            // Get the tooltip lines from the item stack and render
            val tooltipLines = stack.getTooltipLines(
                net.minecraft.world.item.Item.TooltipContext.EMPTY,
                mc.player,
                net.minecraft.world.item.TooltipFlag.NORMAL
            )
            val textLines = tooltipLines.map { it.string }
            guiGraphics.drawTooltip(textLines, mouseX, mouseY)
        }
    }

    /**
     * Get the item ID this slot represents (for navigation/click handling).
     * Returns navigationId if set, otherwise the display itemId.
     */
    fun getItemId(): String = navigationId ?: itemId

    /**
     * Get the slot bounds for click detection.
     * Uses navigationId for the itemId field to enable proper click-through navigation.
     */
    fun getBounds(): SlotBounds = SlotBounds(lastX, lastY, size.pixels, size.pixels, navigationId ?: itemId)

    data class SlotBounds(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val itemId: String
    ) {
        fun contains(mouseX: Int, mouseY: Int): Boolean =
            mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height
    }

    companion object {
        /**
         * Create an ItemSlotWidget from a CustomItemDefinition.
         */
        fun fromCustomItem(
            customItem: CustomItemDefinition,
            count: Int = 1,
            size: SlotSize = SlotSize.NORMAL
        ): ItemSlotWidget {
            return ItemSlotWidget(
                itemId = customItem.filters.baseItem ?: "",
                count = count,
                size = size,
                customItemDef = customItem
            )
        }

        /**
         * Create an ItemSlotWidget from an ItemDefinition.
         * Uses the item's name as the custom display name.
         * Sets navigationId to item.id for proper click-through navigation.
         */
        fun fromItemDefinition(
            item: ItemDefinition,
            count: Int = 1,
            size: SlotSize = SlotSize.NORMAL
        ): ItemSlotWidget {
            return ItemSlotWidget(
                itemId = item.renderItemId,
                count = count,
                size = size,
                itemDefinition = item,
                navigationId = item.id,  // Use the item's database ID for navigation
                customTexture = item.customTexture
            )
        }
    }
}

/**
 * Manages a collection of item slots for batch tooltip rendering and click handling.
 */
class ItemSlotManager {
    private val slots = mutableListOf<ItemSlotWidget>()
    private val bounds = mutableListOf<ItemSlotWidget.SlotBounds>()

    fun clear() {
        slots.clear()
        bounds.clear()
    }

    fun addSlot(slot: ItemSlotWidget) {
        slots.add(slot)
    }

    fun recordBounds(slot: ItemSlotWidget) {
        bounds.add(slot.getBounds())
    }

    /**
     * Render tooltip for the hovered slot, if any.
     */
    fun renderHoveredTooltip(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        for (slot in slots) {
            if (slot.isHovered(mouseX, mouseY)) {
                slot.renderTooltip(guiGraphics, mouseX, mouseY)
                return
            }
        }
    }

    /**
     * Get the item ID at the given position, if any.
     */
    fun getItemAt(mouseX: Int, mouseY: Int): String? {
        return bounds.find { it.contains(mouseX, mouseY) }?.itemId
    }

    /**
     * Register an item region manually (without an ItemSlotWidget).
     * Used for external item rendering.
     */
    fun registerRegion(x: Int, y: Int, width: Int, height: Int, itemId: String) {
        bounds.add(ItemSlotWidget.SlotBounds(x, y, width, height, itemId))
    }
}
