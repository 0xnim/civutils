package xyz.nim.civutils.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import xyz.nim.civutils.data.handbook.ItemDefinition
import xyz.nim.civutils.data.handbook.Recipe
import xyz.nim.civutils.data.handbook.RecipeSlot
import xyz.nim.civutils.data.handbook.RecipeType
import xyz.nim.civutils.models.HandbookModel
import xyz.nim.lib.ui.NlibTheme
import kotlin.math.max
import xyz.nim.civutils.utils.renderOutline

/**
 * Renders recipes visually for the handbook.
 * Supports all recipe types with appropriate layouts.
 */
class RecipeRenderer {

    companion object {
        private const val SLOT_SIZE = 18
        private const val SLOT_SPACING = 2
        private const val ARROW_WIDTH = 24
        private const val SECTION_PADDING = 8
    }

    // Track rendered item slots for tooltip/click handling
    private val itemSlots = mutableListOf<ItemSlotWidget>()

    // Track cycling item slots for tooltip/click handling
    private val cyclingSlots = mutableListOf<CyclingItemSlotWidget>()

    /**
     * Clear tracked item slots. Call this at the start of rendering an item page.
     */
    fun clearSlots() {
        itemSlots.clear()
        cyclingSlots.clear()
    }

    /**
     * Render a recipe and return the height consumed.
     */
    fun render(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        // Don't clear here - accumulate slots across multiple recipes

        return when (recipe.type) {
            RecipeType.CRAFTING_SHAPED -> renderShapedCrafting(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
            RecipeType.CRAFTING_SHAPELESS -> renderShapelessCrafting(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
            RecipeType.CRAFTING_2X2 -> render2x2Crafting(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
            RecipeType.SMELTING, RecipeType.BLASTING, RecipeType.SMOKING, RecipeType.CAMPFIRE ->
                renderFurnaceRecipe(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
            RecipeType.SMITHING -> renderSmithingRecipe(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
            RecipeType.BREWING -> renderBrewingRecipe(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
            RecipeType.STONECUTTING -> renderStonecuttingRecipe(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
            RecipeType.CARTOGRAPHY -> renderCartographyRecipe(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
            RecipeType.CUSTOM -> renderCustomRecipe(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
        }
    }

    /**
     * Render tooltips for hovered item slots.
     */
    fun renderTooltips(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        for (slot in itemSlots) {
            slot.renderTooltip(guiGraphics, mouseX, mouseY)
        }
        for (slot in cyclingSlots) {
            slot.renderTooltip(guiGraphics, mouseX, mouseY)
        }
    }

    /**
     * Get the item ID at the given position, if any.
     */
    fun getItemAt(mouseX: Int, mouseY: Int): String? {
        for (slot in itemSlots) {
            if (slot.isHovered(mouseX, mouseY)) {
                return slot.getItemId()
            }
        }
        for (slot in cyclingSlots) {
            if (slot.isHovered(mouseX, mouseY)) {
                return slot.getItemId()
            }
        }
        return null
    }

    // === SHAPED CRAFTING (3x3) ===

    private fun renderShapedCrafting(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var currentY = y

        // Recipe name
        recipe.name?.let { name ->
            guiGraphics.drawString(font, name, x, currentY, NlibTheme.TEXT_PRIMARY, false)
            currentY += font.lineHeight + 4
        }

        // Determine grid size from pattern
        val pattern = recipe.pattern ?: return currentY - y
        val rows = pattern.size
        val cols = pattern.maxOfOrNull { it.length } ?: 3

        val gridWidth = cols * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING
        val gridHeight = rows * (SLOT_SIZE + SLOT_SPACING) - SLOT_SPACING

        // Background for crafting grid
        guiGraphics.fill(
            x - 2, currentY - 2,
            x + gridWidth + 2, currentY + gridHeight + 2,
            0x40000000
        )

        // Render grid slots
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val slotX = x + col * (SLOT_SIZE + SLOT_SPACING)
                val slotY = currentY + row * (SLOT_SIZE + SLOT_SPACING)

                val char = pattern.getOrNull(row)?.getOrNull(col) ?: ' '
                val recipeSlot = if (char != ' ') recipe.key?.get(char.toString()) else null

                renderSlot(guiGraphics, recipeSlot, slotX, slotY, mouseX, mouseY)
            }
        }

        // Arrow
        val arrowX = x + gridWidth + SECTION_PADDING
        val arrowY = currentY + gridHeight / 2 - 4
        guiGraphics.drawString(font, "→", arrowX, arrowY, NlibTheme.TEXT_SECONDARY, false)

        // Output
        val outputX = arrowX + ARROW_WIDTH
        val output = recipe.outputs?.firstOrNull()
        renderSlot(guiGraphics, output, outputX, currentY + (gridHeight - SLOT_SIZE) / 2, mouseX, mouseY)

        // Output count
        output?.let {
            if (it.count > 1) {
                val countText = "×${it.count}"
                guiGraphics.drawString(
                    font, countText,
                    outputX + SLOT_SIZE + 2,
                    currentY + (gridHeight - SLOT_SIZE) / 2 + (SLOT_SIZE - font.lineHeight) / 2,
                    NlibTheme.TEXT_SECONDARY, false
                )
            }
        }

        return gridHeight + (recipe.name?.let { font.lineHeight + 4 } ?: 0)
    }

    // === SHAPELESS CRAFTING ===

    private fun renderShapelessCrafting(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var currentY = y

        // Recipe name
        recipe.name?.let { name ->
            guiGraphics.drawString(font, name, x, currentY, NlibTheme.TEXT_PRIMARY, false)
            currentY += font.lineHeight + 4
        }

        val ingredients = recipe.ingredients ?: return currentY - y
        val maxPerRow = 9

        // Render ingredients in rows
        var slotX = x
        for ((index, ingredient) in ingredients.withIndex()) {
            if (index > 0 && index % maxPerRow == 0) {
                currentY += SLOT_SIZE + SLOT_SPACING
                slotX = x
            }
            renderSlot(guiGraphics, ingredient, slotX, currentY, mouseX, mouseY)
            slotX += SLOT_SIZE + SLOT_SPACING
        }

        // Arrow and output
        val arrowX = slotX + SECTION_PADDING
        guiGraphics.drawString(font, "→", arrowX, currentY + (SLOT_SIZE - font.lineHeight) / 2, NlibTheme.TEXT_SECONDARY, false)

        val outputX = arrowX + ARROW_WIDTH
        val output = recipe.outputs?.firstOrNull()
        renderSlot(guiGraphics, output, outputX, currentY, mouseX, mouseY)

        return currentY - y + SLOT_SIZE + (recipe.name?.let { font.lineHeight + 4 } ?: 0)
    }

    // === 2x2 CRAFTING ===

    private fun render2x2Crafting(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        // Same as shaped but constrained to 2x2
        return renderShapedCrafting(guiGraphics, recipe, x, y, width, font, mouseX, mouseY)
    }

    // === FURNACE RECIPES ===

    private fun renderFurnaceRecipe(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var currentY = y

        // Recipe name with type
        val typeName = recipe.name ?: recipe.type.displayName
        guiGraphics.drawString(font, typeName, x, currentY, NlibTheme.TEXT_PRIMARY, false)
        currentY += font.lineHeight + 4

        // Input slot
        renderSlot(guiGraphics, recipe.input, x, currentY, mouseX, mouseY)

        // Furnace icon (flame)
        val flameX = x + SLOT_SIZE + SECTION_PADDING
        val icon = when (recipe.type) {
            RecipeType.BLASTING -> "⚡"
            RecipeType.SMOKING -> "🔥"
            RecipeType.CAMPFIRE -> "🏕"
            else -> "🔥"
        }
        guiGraphics.drawString(font, icon, flameX, currentY + (SLOT_SIZE - font.lineHeight) / 2, NlibTheme.TEXT_SECONDARY, false)

        // Arrow
        val arrowX = flameX + 16
        guiGraphics.drawString(font, "→", arrowX, currentY + (SLOT_SIZE - font.lineHeight) / 2, NlibTheme.TEXT_SECONDARY, false)

        // Output slot
        val outputX = arrowX + ARROW_WIDTH
        renderSlot(guiGraphics, recipe.outputs?.firstOrNull(), outputX, currentY, mouseX, mouseY)

        currentY += SLOT_SIZE + 4

        // Cooking time and XP
        val infoText = buildString {
            recipe.cookingTimeSeconds?.let { append("${it}s") }
            recipe.experience?.let {
                if (isNotEmpty()) append(" | ")
                append("${it} XP")
            }
        }
        if (infoText.isNotEmpty()) {
            guiGraphics.drawString(font, infoText, x, currentY, NlibTheme.TEXT_SECONDARY, false)
            currentY += font.lineHeight
        }

        return currentY - y
    }

    // === SMITHING TABLE ===

    private fun renderSmithingRecipe(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var currentY = y

        // Recipe name
        val name = recipe.name ?: "Smithing"
        guiGraphics.drawString(font, name, x, currentY, NlibTheme.TEXT_PRIMARY, false)
        currentY += font.lineHeight + 4

        // Labels
        val labels = listOf("Template", "Base", "Addition")
        val slots = listOf(recipe.template, recipe.base, recipe.addition)

        var slotX = x
        for ((index, label) in labels.withIndex()) {
            // Label above slot
            guiGraphics.drawString(font, label, slotX, currentY, NlibTheme.TEXT_SECONDARY, false)
            slotX += max(font.width(label), SLOT_SIZE) + SLOT_SPACING + 4
        }
        currentY += font.lineHeight + 2

        // Slots
        slotX = x
        for ((index, slot) in slots.withIndex()) {
            renderSlot(guiGraphics, slot, slotX, currentY, mouseX, mouseY)

            // Plus sign between slots
            if (index < slots.size - 1) {
                guiGraphics.drawString(
                    font, "+",
                    slotX + SLOT_SIZE + 4,
                    currentY + (SLOT_SIZE - font.lineHeight) / 2,
                    NlibTheme.TEXT_SECONDARY, false
                )
            }
            slotX += SLOT_SIZE + 16
        }

        // Arrow and output
        guiGraphics.drawString(
            font, "→",
            slotX,
            currentY + (SLOT_SIZE - font.lineHeight) / 2,
            NlibTheme.TEXT_SECONDARY, false
        )
        slotX += ARROW_WIDTH

        renderSlot(guiGraphics, recipe.outputs?.firstOrNull(), slotX, currentY, mouseX, mouseY)

        return currentY - y + SLOT_SIZE + font.lineHeight + 2
    }

    // === BREWING ===

    private fun renderBrewingRecipe(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var currentY = y

        // Recipe name
        val name = recipe.name ?: "Brewing"
        guiGraphics.drawString(font, name, x, currentY, NlibTheme.TEXT_PRIMARY, false)
        currentY += font.lineHeight + 4

        // Ingredient at top center
        val centerX = x + 30
        guiGraphics.drawString(font, "Ingredient", centerX - font.width("Ingredient") / 2, currentY, NlibTheme.TEXT_SECONDARY, false)
        currentY += font.lineHeight + 2

        renderSlot(guiGraphics, recipe.brewIngredient, centerX - SLOT_SIZE / 2, currentY, mouseX, mouseY)
        currentY += SLOT_SIZE + 4

        // Down arrow
        guiGraphics.drawString(font, "↓", centerX - 4, currentY, NlibTheme.TEXT_SECONDARY, false)
        currentY += font.lineHeight + 4

        // Three bottle slots
        guiGraphics.drawString(font, "Bottles", centerX - font.width("Bottles") / 2, currentY, NlibTheme.TEXT_SECONDARY, false)
        currentY += font.lineHeight + 2

        val bottleStartX = centerX - (3 * SLOT_SIZE + 2 * SLOT_SPACING) / 2
        for (i in 0 until 3) {
            renderSlot(guiGraphics, recipe.brewInput, bottleStartX + i * (SLOT_SIZE + SLOT_SPACING), currentY, mouseX, mouseY)
        }

        // Arrow and output
        val arrowX = bottleStartX + 3 * (SLOT_SIZE + SLOT_SPACING) + SECTION_PADDING
        guiGraphics.drawString(font, "→", arrowX, currentY + (SLOT_SIZE - font.lineHeight) / 2, NlibTheme.TEXT_SECONDARY, false)

        val outputX = arrowX + ARROW_WIDTH
        renderSlot(guiGraphics, recipe.outputs?.firstOrNull(), outputX, currentY, mouseX, mouseY)

        return currentY - y + SLOT_SIZE
    }

    // === STONECUTTING ===

    private fun renderStonecuttingRecipe(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var currentY = y

        val name = recipe.name ?: "Stonecutting"
        guiGraphics.drawString(font, name, x, currentY, NlibTheme.TEXT_PRIMARY, false)
        currentY += font.lineHeight + 4

        // Input → Output
        renderSlot(guiGraphics, recipe.stonecutterInput ?: recipe.input, x, currentY, mouseX, mouseY)

        val arrowX = x + SLOT_SIZE + SECTION_PADDING
        guiGraphics.drawString(font, "→", arrowX, currentY + (SLOT_SIZE - font.lineHeight) / 2, NlibTheme.TEXT_SECONDARY, false)

        val outputX = arrowX + ARROW_WIDTH
        renderSlot(guiGraphics, recipe.outputs?.firstOrNull(), outputX, currentY, mouseX, mouseY)

        return currentY - y + SLOT_SIZE
    }

    // === CARTOGRAPHY ===

    private fun renderCartographyRecipe(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var currentY = y

        val name = recipe.name ?: "Cartography Table"
        guiGraphics.drawString(font, name, x, currentY, NlibTheme.TEXT_PRIMARY, false)
        currentY += font.lineHeight + 4

        // Map + Material → Output
        renderSlot(guiGraphics, recipe.mapInput, x, currentY, mouseX, mouseY)

        guiGraphics.drawString(font, "+", x + SLOT_SIZE + 4, currentY + (SLOT_SIZE - font.lineHeight) / 2, NlibTheme.TEXT_SECONDARY, false)

        renderSlot(guiGraphics, recipe.materialInput, x + SLOT_SIZE + 16, currentY, mouseX, mouseY)

        val arrowX = x + 2 * (SLOT_SIZE + 8) + SECTION_PADDING
        guiGraphics.drawString(font, "→", arrowX, currentY + (SLOT_SIZE - font.lineHeight) / 2, NlibTheme.TEXT_SECONDARY, false)

        val outputX = arrowX + ARROW_WIDTH
        renderSlot(guiGraphics, recipe.outputs?.firstOrNull(), outputX, currentY, mouseX, mouseY)

        return currentY - y + SLOT_SIZE
    }

    // === CUSTOM RECIPES ===

    private fun renderCustomRecipe(
        guiGraphics: GuiGraphics,
        recipe: Recipe,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var currentY = y

        // Recipe name
        val name = recipe.name ?: "Recipe"
        guiGraphics.drawString(font, name, x, currentY, NlibTheme.TEXT_PRIMARY, false)
        currentY += font.lineHeight + 4

        // Custom inputs
        val inputs = recipe.customInputs ?: recipe.ingredients ?: emptyList()
        var slotX = x
        for (input in inputs) {
            renderSlot(guiGraphics, input, slotX, currentY, mouseX, mouseY)
            slotX += SLOT_SIZE + SLOT_SPACING
        }

        // Arrow and outputs
        val outputs = recipe.outputs
        if (!outputs.isNullOrEmpty()) {
            val arrowX = slotX + SECTION_PADDING
            guiGraphics.drawString(font, "→", arrowX, currentY + (SLOT_SIZE - font.lineHeight) / 2, NlibTheme.TEXT_SECONDARY, false)

            var outputX = arrowX + ARROW_WIDTH
            for (output in outputs) {
                renderSlot(guiGraphics, output, outputX, currentY, mouseX, mouseY)
                outputX += SLOT_SIZE + SLOT_SPACING
            }
        }

        currentY += SLOT_SIZE + 4

        // Processing time
        recipe.processingTime?.let { time ->
            guiGraphics.drawString(font, time, x, currentY, NlibTheme.TEXT_SECONDARY, false)
            currentY += font.lineHeight
        }

        // Metadata
        val metadata = recipe.metadata
        if (metadata != null) {
            for ((key, value) in metadata) {
                guiGraphics.drawString(font, "$key: $value", x, currentY, NlibTheme.TEXT_SECONDARY, false)
                currentY += font.lineHeight
            }
        }

        return currentY - y
    }

    // === HELPER METHODS ===

    private fun renderSlot(
        guiGraphics: GuiGraphics,
        slot: RecipeSlot?,
        x: Int,
        y: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        if (slot == null || slot.isEmpty) {
            // Empty slot background
            guiGraphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0x40000000)
            guiGraphics.renderOutline(x, y, SLOT_SIZE, SLOT_SIZE, 0x40FFFFFF)
            return
        }

        // Resolve all possible items from tags and alternatives
        val possibleItems = resolvePossibleItems(slot)

        if (possibleItems.size > 1) {
            // Multiple items -> use cycling widget
            val cyclingWidget = CyclingItemSlotWidget(
                items = possibleItems,
                count = slot.count,
                size = ItemSlotWidget.SlotSize.NORMAL
            )
            cyclingWidget.render(guiGraphics, x, y, mouseX, mouseY, renderBackground = true)
            cyclingSlots.add(cyclingWidget)
            return
        }

        // Single item from tags/alternatives -> use regular widget with that item
        if (possibleItems.size == 1) {
            val widget = ItemSlotWidget.fromItemDefinition(possibleItems[0], slot.count, ItemSlotWidget.SlotSize.NORMAL)
            widget.render(guiGraphics, x, y, mouseX, mouseY, renderBackground = true)
            itemSlots.add(widget)
            return
        }

        // Fall back to primary item field
        val itemId = slot.item ?: return // Safety check

        // Check if this is a custom item from the database (no colon = custom ID)
        val widget = if (!itemId.contains(":")) {
            val itemDef = HandbookModel.getItem(itemId)
            if (itemDef != null) {
                // fromItemDefinition already sets navigationId = item.id
                ItemSlotWidget.fromItemDefinition(itemDef, slot.count, ItemSlotWidget.SlotSize.NORMAL)
            } else {
                // Fallback: use resolved display ID but keep original itemId for navigation
                ItemSlotWidget(
                    itemId = resolveItemId(itemId),
                    count = slot.count,
                    size = ItemSlotWidget.SlotSize.NORMAL,
                    navigationId = itemId  // Keep original ID for click navigation
                )
            }
        } else {
            // Vanilla item - itemId is the navigation ID
            ItemSlotWidget(
                itemId = itemId,
                count = slot.count,
                size = ItemSlotWidget.SlotSize.NORMAL
            )
        }

        widget.render(guiGraphics, x, y, mouseX, mouseY, renderBackground = true)
        itemSlots.add(widget)
    }

    /**
     * Resolve all possible items from a recipe slot.
     * Checks tags first, then alternatives, then falls back to the primary item.
     * Returns ItemDefinitions for custom items that can be looked up.
     */
    private fun resolvePossibleItems(slot: RecipeSlot): List<ItemDefinition> {
        val items = mutableListOf<ItemDefinition>()

        // First, resolve items from tags
        slot.tags?.let { tags ->
            items.addAll(HandbookModel.getItemsByTags(tags))
        }

        // Then add alternatives
        slot.alternatives?.forEach { altId ->
            HandbookModel.getItem(altId)?.let { items.add(it) }
        }

        // If we have items from tags or alternatives, use those
        if (items.isNotEmpty()) {
            return items.distinctBy { it.id }
        }

        // Otherwise, try to resolve the primary item
        slot.item?.let { itemId ->
            if (!itemId.contains(":")) {
                HandbookModel.getItem(itemId)?.let { items.add(it) }
            }
        }

        return items.distinctBy { it.id }
    }

    /**
     * Resolve an item ID - if it's a custom item ID (no colon), look up the display item.
     */
    private fun resolveItemId(itemId: String): String {
        return HandbookModel.resolveItemDisplayId(itemId)
    }
}
