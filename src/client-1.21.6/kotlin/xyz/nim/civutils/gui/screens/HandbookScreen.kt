package xyz.nim.civutils.gui.screens

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.data.handbook.*
import xyz.nim.civutils.gui.widgets.Colors
import xyz.nim.civutils.gui.widgets.ItemSlotWidget
import xyz.nim.civutils.gui.widgets.MarkdownRenderer
import xyz.nim.civutils.gui.widgets.RecipeRenderer
import xyz.nim.civutils.models.HandbookModel
import xyz.nim.civutils.utils.ItemMatcher
import xyz.nim.civutils.utils.MarkdownParser
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.lib.mc121.compat.NlibListWidget
import kotlin.math.max

/**
 * Content types for the handbook - either a markdown page or an item from the database.
 */
sealed class PageContent {
    data class MarkdownPage(val page: HandbookPage) : PageContent()
    data class ItemPage(val item: ItemDefinition) : PageContent()
}

/**
 * Screen for viewing handbook content.
 * Features:
 * - Left panel: Category tree and page list with search
 * - Right panel: Rendered markdown content with scrolling
 * - Navigation: Back/forward, page links
 * - Smooth scrolling and draggable scrollbar
 * - Code block copy functionality
 */
class HandbookScreen(
    private val initialPageId: String? = null
) : CivutilsScreen(Component.literal("Handbook")) {

    // Layout
    private var leftPanelX = 0
    private var leftPanelWidth = 0
    private var rightPanelX = 0
    private var rightPanelWidth = 0
    private var contentY = 0
    private var contentHeight = 0

    // Navigation
    private var backButton: Button? = null
    private var forwardButton: Button? = null
    private var searchBox: EditBox? = null
    private var clearSearchButton: Button? = null
    private var searchQuery = ""

    // Search results (for showing match info)
    private var searchResults: Map<String, SearchResult> = emptyMap()

    // Content - can be either markdown page or item page
    private var currentContent: PageContent? = null
    private var contentScroll = 0
    private var maxScroll = 0

    // Parsed markdown for item descriptions
    private val markdownParser = MarkdownParser()
    private var parsedItemDescription: List<MarkdownElement> = emptyList()

    // Cached item relationships (items that use this item, grouped by type)
    private var itemRelationships: List<ItemRelationGroup> = emptyList()

    // Cached element heights (calculated once per page load, not every frame)
    private var cachedElementHeights: List<Int> = emptyList()
    private var cachedDescriptionHeights: List<Int> = emptyList()

    // Scrollbar dragging
    private var isDraggingScrollbar = false
    private var scrollbarDragOffset = 0.0

    // Scrollbar dimensions (cached for hit testing)
    private var scrollbarX = 0
    private var scrollbarTrackY = 0
    private var scrollbarTrackHeight = 0
    private var scrollbarThumbY = 0
    private var scrollbarThumbHeight = 0

    // Lists
    private var categoryList: CategoryListWidget? = null
    private var pageList: PageListWidget? = null
    private var selectedCategory: HandbookCategory? = null

    // Content renderers
    private val markdownRenderer = MarkdownRenderer()
    private val recipeRenderer = RecipeRenderer()

    // Header item slot for item pages
    private var headerItemSlot: ItemSlotWidget? = null

    // Clickable class link regions (for requirement text)
    private data class ClassLinkRegion(val x: Int, val y: Int, val width: Int, val height: Int, val classPage: String)
    private val classLinkRegions = mutableListOf<ClassLinkRegion>()

    // Content area dimensions (for renderer)
    private var contentStartX = 0
    private var contentStartY = 0
    private var contentAreaWidth = 0
    private var contentAreaHeight = 0

    companion object {
        private const val SCROLLBAR_WIDTH = 6
    }

    override fun init() {
        super.init()

        // Layout
        val headerAreaHeight = 50
        leftPanelWidth = (width * 0.3).toInt()
        leftPanelX = layout.margin
        rightPanelX = leftPanelX + leftPanelWidth + layout.spacing
        rightPanelWidth = width - rightPanelX - layout.margin
        contentY = headerAreaHeight
        contentHeight = height - contentY - layout.margin

        // Content area dimensions
        contentStartX = rightPanelX + 10
        contentStartY = contentY + 30
        contentAreaWidth = rightPanelWidth - 20 - SCROLLBAR_WIDTH - 4
        contentAreaHeight = contentHeight - 40

        // Navigation buttons
        backButton = Button.builder(Component.literal("<")) {
            val pageId = HandbookModel.goBack()
            if (pageId != null) loadPage(pageId, addToHistory = false)
        }
            .bounds(leftPanelX, 25, 25, 20)
            .build()
        backButton?.active = HandbookModel.canGoBack()
        addRenderableWidget(backButton!!)

        forwardButton = Button.builder(Component.literal(">")) {
            val pageId = HandbookModel.goForward()
            if (pageId != null) loadPage(pageId, addToHistory = false)
        }
            .bounds(leftPanelX + 28, 25, 25, 20)
            .build()
        forwardButton?.active = HandbookModel.canGoForward()
        addRenderableWidget(forwardButton!!)

        // Search box - spans full width from nav buttons to right margin
        val clearButtonWidth = 20
        val searchBoxX = leftPanelX + 58
        val searchBoxEndX = width - layout.margin - clearButtonWidth - 2
        searchBox = EditBox(font, searchBoxX, 25, searchBoxEndX - searchBoxX, 18, Component.literal(""))
        searchBox?.setHint(Component.literal("Search..."))
        searchBox?.setResponder { query ->
            searchQuery = query
            refreshPageList()
            updateClearButtonVisibility()
        }
        searchBox?.let { addWidget(it) }

        // Clear search button
        clearSearchButton = Button.builder(Component.literal("\u00D7")) { // × symbol
            searchBox?.value = ""
            searchQuery = ""
            refreshPageList()
            updateClearButtonVisibility()
        }
            .bounds(width - layout.margin - clearButtonWidth, 25, clearButtonWidth, 18)
            .build()
        clearSearchButton?.active = false
        clearSearchButton?.visible = false
        addRenderableWidget(clearSearchButton!!)

        // Category list
        val listHeaderOffset = 22
        val categoryListHeight = 80
        categoryList = CategoryListWidget(
            minecraft!!, leftPanelWidth, categoryListHeight,
            contentY + listHeaderOffset, 22
        ) { category ->
            selectedCategory = category
            refreshPageList()
        }
        categoryList?.setX(leftPanelX)

        // Populate categories - markdown categories first, then item categories
        val allCategory = HandbookCategory("__all__", "All", "book", -1)
        categoryList?.addEntryToList(CategoryEntry(allCategory))
        for (category in HandbookModel.getCategories()) {
            categoryList?.addEntryToList(CategoryEntry(category))
        }
        // Add item categories
        for (itemCategory in HandbookModel.getItemCategories()) {
            val handbookCat = HandbookCategory(
                "item:${itemCategory.name}",
                itemCategory.displayName,
                itemCategory.icon,
                100 + itemCategory.ordinal
            )
            categoryList?.addEntryToList(CategoryEntry(handbookCat))
        }
        categoryList?.let { addWidget(it) }

        // Page list
        val pageListY = contentY + listHeaderOffset + categoryListHeight + 8
        val pageListHeight = contentHeight - listHeaderOffset - categoryListHeight - 8
        pageList = PageListWidget(
            minecraft!!, leftPanelWidth, pageListHeight,
            pageListY, 28
        ) { contentId ->
            loadPage(contentId)
        }
        pageList?.setX(leftPanelX)
        pageList?.let { addWidget(it) }

        refreshPageList()

        // Load initial page
        val pageToLoad = initialPageId ?: HandbookModel.getIndex().defaultPage
        if (pageToLoad.isNotEmpty()) {
            loadPage(pageToLoad)
        }

        // Auto-focus search box for immediate typing
        // Call both setInitialFocus and setFocused directly on the EditBox
        // to ensure focus works reliably across different systems
        searchBox?.let {
            setInitialFocus(it)
            it.setFocused(true)
        }
    }

    private fun refreshPageList() {
        pageList?.clearEntries()

        if (searchQuery.isNotBlank()) {
            // Unified search with relevance scoring - items and pages ranked together
            val unifiedResults = HandbookModel.unifiedSearch(searchQuery)

            // Build searchResults map for page match info display
            searchResults = unifiedResults
                .filterIsInstance<ScoredSearchResult.PageResult>()
                .associate { it.page.id to SearchResult(it.page, it.matchTypes, it.matchSnippet) }

            for (result in unifiedResults) {
                when (result) {
                    is ScoredSearchResult.PageResult -> {
                        val searchResult = SearchResult(result.page, result.matchTypes, result.matchSnippet)
                        pageList?.addEntryToList(PageEntry(result.page, searchResult))
                    }
                    is ScoredSearchResult.ItemResult -> {
                        pageList?.addEntryToList(ItemEntry(result.item))
                    }
                }
            }
        } else {
            // Browse mode - show by category
            searchResults = emptyMap()
            val categoryId = selectedCategory?.id

            when {
                categoryId == null || categoryId == "__all__" -> {
                    // Show all pages and items
                    for (page in HandbookModel.getPages().sortedBy { it.order }) {
                        pageList?.addEntryToList(PageEntry(page, null))
                    }
                    for (item in HandbookModel.getAllItems().sortedBy { it.order }) {
                        pageList?.addEntryToList(ItemEntry(item))
                    }
                }
                categoryId.startsWith("item:") -> {
                    // Show items in this item category
                    val itemCatName = categoryId.removePrefix("item:")
                    val itemCategory = ItemCategory.entries.find { it.name == itemCatName }
                    if (itemCategory != null) {
                        for (item in HandbookModel.getItemsByCategory(itemCategory)) {
                            pageList?.addEntryToList(ItemEntry(item))
                        }
                    }
                }
                else -> {
                    // Show markdown pages in this category
                    for (page in HandbookModel.getPagesInCategory(categoryId).sortedBy { it.order }) {
                        pageList?.addEntryToList(PageEntry(page, null))
                    }
                }
            }
        }
    }

    private fun updateClearButtonVisibility() {
        val hasQuery = searchQuery.isNotBlank()
        clearSearchButton?.active = hasQuery
        clearSearchButton?.visible = hasQuery
    }

    private fun loadPage(pageId: String, addToHistory: Boolean = true) {
        contentScroll = 0
        parsedItemDescription = emptyList()
        itemRelationships = emptyList()
        cachedElementHeights = emptyList()
        cachedDescriptionHeights = emptyList()

        // Try to find content by ID - check items database first, then markdown pages
        val item = HandbookModel.getItem(pageId)
        val page = HandbookModel.getPage(pageId)

        currentContent = when {
            item != null -> PageContent.ItemPage(item)
            page != null -> PageContent.MarkdownPage(page)
            else -> null
        }

        if (addToHistory && currentContent != null) {
            HandbookModel.navigateTo(pageId)
        }

        // Update navigation button states
        backButton?.active = HandbookModel.canGoBack()
        forwardButton?.active = HandbookModel.canGoForward()

        // Setup header and calculate scroll based on content type
        when (val content = currentContent) {
            is PageContent.ItemPage -> setupItemPage(content.item)
            is PageContent.MarkdownPage -> setupMarkdownPage(content.page)
            null -> {
                headerItemSlot = null
                maxScroll = 0
            }
        }
    }

    private fun setupItemPage(item: ItemDefinition) {
        // Create header item slot using the item definition (shows custom name)
        headerItemSlot = ItemSlotWidget.fromItemDefinition(item, 1, ItemSlotWidget.SlotSize.LARGE)

        // Parse markdown description if present
        val description = item.description
        if (!description.isNullOrBlank()) {
            parsedItemDescription = markdownParser.parse(description)
        }

        // Cache description element heights
        cachedDescriptionHeights = parsedItemDescription.map { element ->
            element.calculateHeight(contentAreaWidth, font)
        }

        // Compute item relationships (items that use this as an ingredient, grouped by type)
        itemRelationships = HandbookModel.getItemRelationships(item.id)

        // Calculate max scroll for item page
        var totalHeight = calculateItemPageHeight(item)
        maxScroll = max(0, totalHeight - contentAreaHeight)
    }

    private fun setupMarkdownPage(page: HandbookPage) {
        // Create header item slot if this is an item page
        headerItemSlot = when {
            // Custom item with NBT filters (uses ItemSlotWidget.fromCustomItem)
            page.meta.customItemId != null -> {
                val customDef = ItemMatcher.getDefinition(page.meta.customItemId!!)
                if (customDef != null) {
                    ItemSlotWidget.fromCustomItem(customDef, 1, ItemSlotWidget.SlotSize.LARGE)
                } else null
            }
            // Standard vanilla item
            page.meta.itemId != null -> {
                ItemSlotWidget(page.meta.itemId!!, 1, ItemSlotWidget.SlotSize.LARGE)
            }
            else -> null
        }

        // Cache element heights (calculate once, not every frame)
        cachedElementHeights = page.renderedContent.map { element ->
            element.calculateHeight(contentAreaWidth, font)
        }

        // Calculate max scroll using cached heights
        var totalHeight = 0
        var isFirst = true
        for ((index, element) in page.renderedContent.withIndex()) {
            // Top margin for headings
            if (!isFirst && element is HeadingElement) {
                totalHeight += when (element.level) {
                    1 -> 16
                    2 -> 12
                    else -> 8
                }
            }
            // Use cached height
            totalHeight += cachedElementHeights[index]
            // Bottom margin
            totalHeight += when (element) {
                is HeadingElement -> 8
                is TableElement -> 10
                is CodeBlockElement -> 10
                is ListElement -> 8
                else -> 6
            }
            isFirst = false
        }
        maxScroll = max(0, totalHeight - contentAreaHeight)
    }

    private fun calculateItemPageHeight(item: ItemDefinition): Int {
        var height = 0

        // Summary
        if (!item.summary.isNullOrBlank()) {
            height += font.lineHeight + 8
        }

        // Required class
        if (item.requiredClass != null) {
            height += font.lineHeight + 4
        }

        // Interaction requirement
        if (item.interactionRequirement != null) {
            height += font.lineHeight + 4
        }

        // Mining requirement
        if (item.miningRequirement != null) {
            height += font.lineHeight + 4
        }

        // Metadata section
        val metadata = item.metadata
        if (!metadata.isNullOrEmpty()) {
            // dropsFrom and dropsWhenBroken render as item slots (header + slot row)
            if (metadata["dropsFrom"] != null) {
                height += font.lineHeight + 4 + 24  // Header + slot row
            }
            if (metadata["dropsWhenBroken"] != null) {
                height += font.lineHeight + 4 + 24  // Header + slot row
            }
            // Other metadata as text
            val textMetadataCount = metadata.keys.count { it !in listOf("dropsFrom", "dropsWhenBroken") }
            height += textMetadataCount * (font.lineHeight + 4) + 4
        }

        // Recipes section
        if (!item.recipes.isNullOrEmpty()) {
            height += font.lineHeight + 8 // "Recipes" header
            for (recipe in item.recipes) {
                height += estimateRecipeHeight(recipe) + 12
            }
        }

        // Description (markdown) - use cached heights if available
        for ((index, element) in parsedItemDescription.withIndex()) {
            height += cachedDescriptionHeights.getOrElse(index) { element.baseHeight } + 6
        }

        // Used in section
        if (!item.usedIn.isNullOrEmpty()) {
            height += font.lineHeight + 8 // "Used In" header
            height += 24 // Item slots row
        }

        // Related section
        if (!item.related.isNullOrEmpty()) {
            height += font.lineHeight + 8 // "Related" header
            height += 24 // Item slots row
        }

        // Item relationships section (items that use this as an ingredient)
        for (group in itemRelationships) {
            height += font.lineHeight + 8 // Header (e.g., "Ingredient For")
            // Calculate rows needed for items (max items per row based on content width)
            val itemsPerRow = (contentAreaWidth / 20).coerceAtLeast(1)
            val rows = (group.items.size + itemsPerRow - 1) / itemsPerRow
            height += rows * 24 + 8
        }

        return height
    }

    private fun estimateRecipeHeight(recipe: Recipe): Int {
        return when (recipe.type) {
            RecipeType.CRAFTING_SHAPED -> {
                val rows = recipe.pattern?.size ?: 3
                rows * 20 + (recipe.name?.let { font.lineHeight + 4 } ?: 0)
            }
            RecipeType.SMITHING -> font.lineHeight * 2 + 24
            RecipeType.BREWING -> font.lineHeight * 4 + 60
            else -> 40
        }
    }

    override fun tick() {
        super.tick()
    }

    override fun renderPanels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Title
        guiGraphics.drawCenteredString(font, title, width / 2, 8, NlibTheme.TEXT_PRIMARY)

        // Panels
        drawPanel(guiGraphics, leftPanelX, contentY, leftPanelWidth, contentHeight)
        drawPanel(guiGraphics, rightPanelX, contentY, rightPanelWidth, contentHeight)

        // Search box
        searchBox?.render(guiGraphics, mouseX, mouseY, partialTick)

        // Lists
        categoryList?.render(guiGraphics, mouseX, mouseY, partialTick)
        pageList?.render(guiGraphics, mouseX, mouseY, partialTick)

        // Render markdown content
        renderContent(guiGraphics, mouseX, mouseY)
    }

    private fun renderContent(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        val content = currentContent

        if (content == null) {
            // Show placeholder
            val placeholder = if (HandbookModel.hasContent()) {
                "Select a page from the list"
            } else {
                "No handbook content available"
            }
            guiGraphics.drawCenteredString(
                font, placeholder,
                rightPanelX + rightPanelWidth / 2,
                contentY + contentHeight / 2,
                NlibTheme.TEXT_SECONDARY
            )
            return
        }

        // Set up scissor for content area
        guiGraphics.enableScissor(
            contentStartX, contentStartY,
            contentStartX + contentAreaWidth, contentStartY + contentAreaHeight
        )

        markdownRenderer.clearRegions()

        // Check if mouse is in content area - use raw screen coordinates for hit detection
        // since regions are registered at actual screen positions (scroll-adjusted render positions)
        val isMouseInContent = mouseX in contentStartX..(contentStartX + contentAreaWidth) &&
            mouseY in contentStartY..(contentStartY + contentAreaHeight)
        val effectiveMouseY = if (isMouseInContent) mouseY else -1

        when (content) {
            is PageContent.MarkdownPage -> renderMarkdownContent(guiGraphics, content.page, effectiveMouseY)
            is PageContent.ItemPage -> renderItemContent(guiGraphics, content.item, mouseX, effectiveMouseY)
        }

        guiGraphics.disableScissor()

        // Update hover state after rendering (link regions are now populated)
        markdownRenderer.updateHover(mouseX, effectiveMouseY)

        // Scrollbar
        renderScrollbar(guiGraphics, mouseX, mouseY)
    }

    private fun renderMarkdownContent(guiGraphics: GuiGraphics, page: HandbookPage, adjustedMouseY: Int) {
        var y = contentStartY - contentScroll
        var isFirst = true

        for ((index, element) in page.renderedContent.withIndex()) {
            // Add extra top margin before headings (except the first element)
            val topMargin = if (!isFirst && element is HeadingElement) {
                when (element.level) {
                    1 -> 16
                    2 -> 12
                    else -> 8
                }
            } else {
                0
            }
            y += topMargin

            // Use cached height instead of recalculating every frame
            val elementHeight = cachedElementHeights.getOrElse(index) { element.baseHeight }

            // Calculate bottom margin based on element type
            val bottomMargin = when (element) {
                is HeadingElement -> 8
                is TableElement -> 10
                is CodeBlockElement -> 10
                is ListElement -> 8
                else -> 6
            }

            // Skip elements above visible area
            if (y + elementHeight < contentStartY) {
                y += elementHeight + bottomMargin
                isFirst = false
                continue
            }

            // Stop rendering below visible area
            if (y > contentStartY + contentAreaHeight) break

            markdownRenderer.render(guiGraphics, element, contentStartX, y, contentAreaWidth, font)
            y += elementHeight + bottomMargin
            isFirst = false
        }
    }

    private fun renderItemContent(guiGraphics: GuiGraphics, item: ItemDefinition, mouseX: Int, mouseY: Int) {
        var y = contentStartY - contentScroll

        // Clear renderers at the start of each frame
        recipeRenderer.clearSlots()
        classLinkRegions.clear()

        // Summary
        val summary = item.summary
        if (!summary.isNullOrBlank()) {
            guiGraphics.drawString(font, summary, contentStartX, y, NlibTheme.TEXT_SECONDARY, false)
            y += font.lineHeight + 8
        }

        // Required class (clickable link to class page)
        item.requiredClassInfo?.let { (className, level) ->
            y = renderClassRequirement(guiGraphics, "Craft:", className, level, y, mouseX, mouseY)
        }

        // Interaction requirement (clickable link to class page)
        item.interactionRequirementInfo?.let { (className, level) ->
            y = renderClassRequirement(guiGraphics, "Interact:", className, level, y, mouseX, mouseY)
        }

        // Mining requirement (clickable link to class page)
        item.miningRequirementInfo?.let { (className, level) ->
            y = renderClassRequirement(guiGraphics, "Mine:", className, level, y, mouseX, mouseY)
        }

        // Metadata section (dropsFrom, dropsWhenBroken, etc.)
        val metadata = item.metadata
        if (!metadata.isNullOrEmpty()) {
            val dropsFrom = metadata["dropsFrom"]
            val dropsWhenBroken = metadata["dropsWhenBroken"]
            val bestYLevel = metadata["bestYLevel"]
            val bestBiomes = metadata["bestBiomes"]

            // Obtained from - render as clickable item slots
            if (dropsFrom != null) {
                guiGraphics.drawString(font, "§lObtained From", contentStartX, y, NlibTheme.TEXT_PRIMARY, false)
                y += font.lineHeight + 4

                var slotX = contentStartX
                // Parse comma-separated item names and find matching items
                val sourceNames = dropsFrom.split(",").map { it.trim() }
                for (sourceName in sourceNames) {
                    // Try to find item by name match
                    val sourceItem = HandbookModel.getAllItems().find {
                        it.name.equals(sourceName, ignoreCase = true)
                    }
                    if (sourceItem != null) {
                        val slot = ItemSlotWidget.fromItemDefinition(sourceItem, 1, ItemSlotWidget.SlotSize.NORMAL)
                        slot.render(guiGraphics, slotX, y, mouseX, mouseY, renderBackground = true)
                        markdownRenderer.addItemSlot(slot)
                        slotX += 20
                    }
                }
                y += 24
            }

            // Drops when broken - render as clickable item slots
            if (dropsWhenBroken != null) {
                guiGraphics.drawString(font, "§lDrops", contentStartX, y, NlibTheme.TEXT_PRIMARY, false)
                y += font.lineHeight + 4

                var slotX = contentStartX
                // Parse "Item Name (count)" format
                val dropMatch = Regex("(.+?)\\s*\\(([^)]+)\\)").find(dropsWhenBroken)
                val dropName = dropMatch?.groupValues?.get(1)?.trim() ?: dropsWhenBroken
                val dropCount = dropMatch?.groupValues?.get(2) ?: "1"

                val dropItem = HandbookModel.getAllItems().find {
                    it.name.equals(dropName, ignoreCase = true)
                }
                if (dropItem != null) {
                    val slot = ItemSlotWidget.fromItemDefinition(dropItem, 1, ItemSlotWidget.SlotSize.NORMAL)
                    slot.render(guiGraphics, slotX, y, mouseX, mouseY, renderBackground = true)
                    markdownRenderer.addItemSlot(slot)
                    slotX += 20
                    // Show count next to slot
                    guiGraphics.drawString(font, "×$dropCount", slotX, y + 4, NlibTheme.TEXT_SECONDARY, false)
                }
                y += 24
            }

            // Best Y-Level and Best Biomes as text
            if (bestYLevel != null) {
                guiGraphics.drawString(font, "§7Best Y-Level: §f$bestYLevel", contentStartX, y, NlibTheme.TEXT_PRIMARY, false)
                y += font.lineHeight + 4
            }
            if (bestBiomes != null) {
                guiGraphics.drawString(font, "§7Best Biomes: §f$bestBiomes", contentStartX, y, NlibTheme.TEXT_PRIMARY, false)
                y += font.lineHeight + 4
            }

            // Any other metadata
            for ((key, value) in metadata) {
                if (key !in listOf("dropsFrom", "dropsWhenBroken", "bestYLevel", "bestBiomes")) {
                    val displayKey = key.replace(Regex("([A-Z])"), " $1").trim().replaceFirstChar { it.uppercase() }
                    guiGraphics.drawString(font, "§7$displayKey: §f$value", contentStartX, y, NlibTheme.TEXT_PRIMARY, false)
                    y += font.lineHeight + 4
                }
            }
            y += 4
        }

        // Recipes section
        val recipes = item.recipes
        if (!recipes.isNullOrEmpty()) {
            guiGraphics.drawString(font, "§lRecipes", contentStartX, y, NlibTheme.TEXT_PRIMARY, false)
            y += font.lineHeight + 8

            for (recipe in recipes) {
                val recipeHeight = recipeRenderer.render(
                    guiGraphics, recipe, contentStartX, y,
                    contentAreaWidth, font, mouseX, mouseY
                )
                y += recipeHeight + 12
            }
        }

        // Markdown description - use cached heights
        if (parsedItemDescription.isNotEmpty()) {
            y += 8 // Extra spacing before description
            for ((index, element) in parsedItemDescription.withIndex()) {
                val elementHeight = cachedDescriptionHeights.getOrElse(index) { element.baseHeight }
                if (y + elementHeight >= contentStartY && y <= contentStartY + contentAreaHeight) {
                    markdownRenderer.render(guiGraphics, element, contentStartX, y, contentAreaWidth, font)
                }
                y += elementHeight + 6
            }
        }

        // Used In section
        val usedIn = item.usedIn
        if (!usedIn.isNullOrEmpty()) {
            y += 8
            guiGraphics.drawString(font, "§lUsed In", contentStartX, y, NlibTheme.TEXT_PRIMARY, false)
            y += font.lineHeight + 4

            var slotX = contentStartX
            for (usedInId in usedIn) {
                val usedInItem = HandbookModel.getItem(usedInId)
                if (usedInItem != null) {
                    val slot = ItemSlotWidget.fromItemDefinition(usedInItem, 1, ItemSlotWidget.SlotSize.NORMAL)
                    slot.render(guiGraphics, slotX, y, mouseX, mouseY, renderBackground = true)
                    markdownRenderer.addItemSlot(slot)
                    slotX += 20
                }
            }
            y += 24
        }

        // Related section
        val related = item.related
        if (!related.isNullOrEmpty()) {
            y += 8
            guiGraphics.drawString(font, "§lRelated", contentStartX, y, NlibTheme.TEXT_PRIMARY, false)
            y += font.lineHeight + 4

            var slotX = contentStartX
            for (relatedId in related) {
                val relatedItem = HandbookModel.getItem(relatedId)
                if (relatedItem != null) {
                    val slot = ItemSlotWidget.fromItemDefinition(relatedItem, 1, ItemSlotWidget.SlotSize.NORMAL)
                    slot.render(guiGraphics, slotX, y, mouseX, mouseY, renderBackground = true)
                    markdownRenderer.addItemSlot(slot)
                    slotX += 20
                }
            }
            y += 24
        }

        // Item relationships section (items that use this as an ingredient, grouped by type)
        for (group in itemRelationships) {
            y += 8
            guiGraphics.drawString(font, "§l${group.type.displayName}", contentStartX, y, NlibTheme.TEXT_PRIMARY, false)
            y += font.lineHeight + 4

            // Render items as a row of clickable slots
            var slotX = contentStartX
            val itemsPerRow = (contentAreaWidth / 20).coerceAtLeast(1)

            for ((index, relatedItem) in group.items.withIndex()) {
                // Wrap to next row if needed
                if (index > 0 && index % itemsPerRow == 0) {
                    y += 24
                    slotX = contentStartX
                }

                val slot = ItemSlotWidget.fromItemDefinition(relatedItem, 1, ItemSlotWidget.SlotSize.NORMAL)
                slot.render(guiGraphics, slotX, y, mouseX, mouseY, renderBackground = true)
                markdownRenderer.addItemSlot(slot)
                slotX += 20
            }
            y += 24
        }
    }

    /**
     * Render a class requirement as a clickable link.
     * Format: "Label: ClassName Lv. N" where the class name is underlined and clickable.
     */
    private fun renderClassRequirement(
        guiGraphics: GuiGraphics,
        label: String,
        className: String,
        level: Int,
        startY: Int,
        mouseX: Int,
        mouseY: Int
    ): Int {
        val displayName = className.replaceFirstChar { it.uppercase() }
        val classText = "$displayName Lv. $level"

        // Calculate positions
        val labelWidth = font.width(label)
        val classTextX = contentStartX + labelWidth + 4
        val classTextWidth = font.width(classText)

        // Check if hovering over the class text
        val isHovering = mouseX >= classTextX && mouseX < classTextX + classTextWidth &&
                mouseY >= startY && mouseY < startY + font.lineHeight

        // Render label
        guiGraphics.drawString(font, label, contentStartX, startY, NlibTheme.TEXT_SECONDARY, false)

        // Render class name (underlined if hovering)
        val classColor = if (isHovering) Colors.LINK_HOVER else Colors.ACCENT
        guiGraphics.drawString(font, classText, classTextX, startY, classColor, false)

        // Draw underline
        val underlineY = startY + font.lineHeight
        guiGraphics.fill(classTextX, underlineY, classTextX + classTextWidth, underlineY + 1, classColor)

        // Register click region
        classLinkRegions.add(ClassLinkRegion(classTextX, startY, classTextWidth, font.lineHeight, className))

        return startY + font.lineHeight + 8
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (maxScroll <= 0) return

        scrollbarX = rightPanelX + rightPanelWidth - SCROLLBAR_WIDTH - 2
        scrollbarTrackY = contentStartY
        scrollbarTrackHeight = contentAreaHeight

        scrollbarThumbHeight = max(20, (contentAreaHeight * contentAreaHeight) / (contentAreaHeight + maxScroll))
        scrollbarThumbY = scrollbarTrackY + ((contentScroll * (scrollbarTrackHeight - scrollbarThumbHeight)) / maxScroll)

        // Track background
        guiGraphics.fill(
            scrollbarX, scrollbarTrackY,
            scrollbarX + SCROLLBAR_WIDTH, scrollbarTrackY + scrollbarTrackHeight,
            0x40FFFFFF
        )

        // Check if hovering over scrollbar
        val isHoveringTrack = mouseX in scrollbarX..(scrollbarX + SCROLLBAR_WIDTH) &&
                mouseY in scrollbarTrackY..(scrollbarTrackY + scrollbarTrackHeight)
        val isHoveringThumb = mouseX in scrollbarX..(scrollbarX + SCROLLBAR_WIDTH) &&
                mouseY in scrollbarThumbY..(scrollbarThumbY + scrollbarThumbHeight)

        // Thumb
        val thumbColor = when {
            isDraggingScrollbar -> 0xFFFFFFFF.toInt()
            isHoveringThumb -> 0xDDFFFFFF.toInt()
            isHoveringTrack -> 0xBBFFFFFF.toInt()
            else -> 0xAAFFFFFF.toInt()
        }
        guiGraphics.fill(
            scrollbarX, scrollbarThumbY,
            scrollbarX + SCROLLBAR_WIDTH, scrollbarThumbY + scrollbarThumbHeight,
            thumbColor
        )
    }

    override fun renderOverlays(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Panel headers
        guiGraphics.drawString(font, "Categories", leftPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)

        // Right panel header with optional item icon
        val headerX = rightPanelX + 8
        val headerY = contentY + 6

        // Render item icon if this is an item page
        val itemSlot = headerItemSlot
        val titleX = if (itemSlot != null) {
            // Render item icon
            itemSlot.render(guiGraphics, headerX, headerY - 4, mouseX, mouseY, renderBackground = false)
            headerX + ItemSlotWidget.SlotSize.LARGE.pixels + 4
        } else {
            headerX
        }

        // Get title and category based on content type
        val (rightTitle, breadcrumb) = when (val content = currentContent) {
            is PageContent.MarkdownPage -> {
                val page = content.page
                val category = HandbookModel.getCategories().find { it.id == page.meta.category }
                val crumb = category?.let { "${it.name} > ${page.meta.title}" }
                page.meta.title to crumb
            }
            is PageContent.ItemPage -> {
                val item = content.item
                val crumb = "${item.category.displayName} > ${item.name}"
                item.name to crumb
            }
            null -> "Select a page" to null
        }

        guiGraphics.drawString(font, rightTitle, titleX, headerY, NlibTheme.TEXT_PRIMARY, true)

        // Breadcrumb
        if (breadcrumb != null) {
            guiGraphics.drawString(font, breadcrumb, titleX, contentY + 18, NlibTheme.TEXT_SECONDARY, false)
        }

        // Render item tooltips (after all content so they appear on top)
        // Use raw screen coordinates since regions are registered at screen positions
        markdownRenderer.renderItemTooltips(guiGraphics, mouseX, mouseY)

        // Render recipe tooltips for item pages
        if (currentContent is PageContent.ItemPage) {
            recipeRenderer.renderTooltips(guiGraphics, mouseX, mouseY)
        }

        // Render header item slot tooltip
        itemSlot?.renderTooltip(guiGraphics, mouseX, mouseY)
    }

    override fun mouseScrolled(
        mouseX: Double,
        mouseY: Double,
        horizontalAmount: Double,
        verticalAmount: Double
    ): Boolean {
        // Check if mouse is over content area
        if (mouseX >= rightPanelX && mouseX < rightPanelX + rightPanelWidth &&
            mouseY >= contentY && mouseY < contentY + contentHeight
        ) {
            contentScroll = (contentScroll - (verticalAmount * 30).toInt()).coerceIn(0, maxScroll)
            return true
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0) {
            // Check scrollbar click
            if (maxScroll > 0 && mouseX >= scrollbarX && mouseX < scrollbarX + SCROLLBAR_WIDTH) {
                if (mouseY >= scrollbarThumbY && mouseY < scrollbarThumbY + scrollbarThumbHeight) {
                    // Clicking on thumb - start drag
                    isDraggingScrollbar = true
                    scrollbarDragOffset = mouseY - scrollbarThumbY
                    return true
                } else if (mouseY >= scrollbarTrackY && mouseY < scrollbarTrackY + scrollbarTrackHeight) {
                    // Clicking on track - jump to position
                    val clickRatio = (mouseY - scrollbarTrackY - scrollbarThumbHeight / 2) /
                            (scrollbarTrackHeight - scrollbarThumbHeight)
                    contentScroll = (clickRatio * maxScroll).toInt().coerceIn(0, maxScroll)
                    return true
                }
            }

            // Handle content area clicks
            val content = currentContent
            if (content != null) {
                if (mouseX >= contentStartX && mouseX < contentStartX + contentAreaWidth &&
                    mouseY >= contentStartY && mouseY < contentStartY + contentAreaHeight
                ) {
                    // Use raw screen coordinates since regions are registered at screen positions
                    val clickY = mouseY.toInt()

                    // Check for code block copy button click
                    val code = markdownRenderer.getCodeBlockAt(mouseX.toInt(), clickY)
                    if (code != null) {
                        copyToClipboard(code)
                        toastManager.info("Copied to clipboard")
                        return true
                    }

                    // Check for item click (from markdown or recipe renderer)
                    val itemId = markdownRenderer.getItemAt(mouseX.toInt(), clickY)
                        ?: (if (content is PageContent.ItemPage) recipeRenderer.getItemAt(mouseX.toInt(), clickY) else null)
                    if (itemId != null) {
                        handleItemClick(itemId)
                        return true
                    }

                    // Check for class link click (requirement text)
                    val classLink = classLinkRegions.find { region ->
                        mouseX.toInt() >= region.x && mouseX.toInt() < region.x + region.width &&
                        clickY >= region.y && clickY < region.y + region.height
                    }
                    if (classLink != null) {
                        loadPage(classLink.classPage)
                        return true
                    }

                    // Check for link click
                    val link = markdownRenderer.getLinkAt(mouseX.toInt(), clickY)
                    if (link != null) {
                        handleLinkClick(link)
                        return true
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isDraggingScrollbar) {
            isDraggingScrollbar = false
            return true
        }
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDraggingScrollbar && maxScroll > 0) {
            val newThumbY = mouseY - scrollbarDragOffset
            val scrollRatio = (newThumbY - scrollbarTrackY) / (scrollbarTrackHeight - scrollbarThumbHeight)
            contentScroll = (scrollRatio * maxScroll).toInt().coerceIn(0, maxScroll)
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    private fun copyToClipboard(text: String) {
        minecraft?.keyboardHandler?.clipboard = text
    }

    private fun handleLinkClick(link: String) {
        when {
            link.startsWith("http://") || link.startsWith("https://") -> {
                CivutilsMod.logger.info("External link clicked: $link")
                toastManager.info("External links not supported")
            }

            else -> {
                // Internal page or item link
                if (HandbookModel.getItem(link) != null || HandbookModel.getPage(link) != null) {
                    loadPage(link)
                } else {
                    toastManager.error("Page not found: $link")
                }
            }
        }
    }

    /**
     * Handle click on an item slot - navigate to the item's page if one exists.
     */
    private fun handleItemClick(itemId: String) {
        // First check items database with exact ID
        var item = HandbookModel.getItem(itemId)
        if (item != null) {
            loadPage(item.id)
            return
        }

        // Try without minecraft: prefix (e.g., minecraft:copper_ingot -> copper_ingot)
        if (itemId.startsWith("minecraft:")) {
            val shortId = itemId.removePrefix("minecraft:")
            item = HandbookModel.getItem(shortId)
            if (item != null) {
                loadPage(item.id)
                return
            }
        }

        // Find a page with this itemId (vanilla or custom)
        val itemPage = HandbookModel.getPages().find {
            it.itemId == itemId || it.customItemId == itemId
        }
        if (itemPage != null) {
            loadPage(itemPage.id)
        } else {
            // No page for this item - just show the item name
            CivutilsMod.logger.debug("No handbook page for item: $itemId")
        }
    }

    // === List Widgets ===

    private inner class CategoryListWidget(
        client: Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int,
        private val onSelect: (HandbookCategory) -> Unit
    ) : NlibListWidget<CategoryEntry>(client, width, height, y, itemHeight) {

        override fun setSelected(entry: CategoryEntry?) {
            super.setSelected(entry)
            entry?.category?.let { onSelect(it) }
        }
    }

    private inner class CategoryEntry(val category: HandbookCategory) : NlibListWidget.Entry<CategoryEntry>() {
        override fun renderContent(
            guiGraphics: GuiGraphics,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float
        ) {
            val x = getX()
            val y = getY()
            val entryWidth = getWidth()
            val entryHeight = getHeight()
            val selected = (categoryList?.selected === this)
            renderBackground(guiGraphics, x, y, entryWidth, entryHeight, hovered, selected)

            val itemCount = when {
                category.id == "__all__" -> HandbookModel.getPages().size + HandbookModel.getAllItems().size
                category.id.startsWith("item:") -> {
                    val itemCatName = category.id.removePrefix("item:")
                    val itemCategory = ItemCategory.entries.find { it.name == itemCatName }
                    itemCategory?.let { HandbookModel.getItemsByCategory(it).size } ?: 0
                }
                else -> HandbookModel.getPagesInCategory(category.id).size
            }

            val text = "${category.name} ($itemCount)"
            guiGraphics.drawString(font, text, x + 8, y + 6, Colors.TEXT, true)
        }
    }

    private inner class PageListWidget(
        client: Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int,
        private val onSelect: (String) -> Unit  // Now takes content ID
    ) : NlibListWidget<ContentListEntry>(client, width, height, y, itemHeight) {

        override fun setSelected(entry: ContentListEntry?) {
            super.setSelected(entry)
            entry?.contentId?.let { onSelect(it) }
        }
    }

    /** Base class for list entries */
    private abstract inner class ContentListEntry : NlibListWidget.Entry<ContentListEntry>() {
        abstract val contentId: String
    }

    private inner class PageEntry(
        val page: HandbookPageMeta,
        val searchResult: SearchResult?
    ) : ContentListEntry() {

        override val contentId: String get() = page.id

        override fun renderContent(
            guiGraphics: GuiGraphics,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float
        ) {
            val x = getX()
            val y = getY()
            val entryWidth = getWidth()
            val entryHeight = getHeight()
            val currentId = (currentContent as? PageContent.MarkdownPage)?.page?.meta?.id
            val selected = (pageList?.selected === this) || (currentId == page.id)
            renderBackground(guiGraphics, x, y, entryWidth, entryHeight, hovered, selected)

            // Title
            guiGraphics.drawString(font, page.title, x + 8, y + 4, Colors.TEXT, true)

            // Show match type indicators when in search mode
            if (searchResult != null && searchResult.matchTypes.isNotEmpty()) {
                val indicatorX = x + entryWidth - 8
                renderMatchIndicators(guiGraphics, indicatorX, y + 4, searchResult.matchTypes)
            }

            // Show snippet or summary in second line
            val secondLine = if (searchResult?.matchSnippet != null && searchQuery.isNotBlank()) {
                // Show content snippet with query highlighted
                searchResult.matchSnippet
            } else if (page.summary.isNotEmpty()) {
                page.summary
            } else {
                null
            }

            if (secondLine != null) {
                val maxLen = (entryWidth - 16) / 4
                val displayText = if (secondLine.length > maxLen) {
                    secondLine.take(maxLen - 3) + "..."
                } else {
                    secondLine
                }
                guiGraphics.drawString(font, displayText, x + 8, y + 16, Colors.TEXT_SECONDARY, false)
            }
        }

        private fun renderMatchIndicators(
            guiGraphics: GuiGraphics,
            rightX: Int,
            y: Int,
            matchTypes: Set<SearchMatchType>
        ) {
            // Render small colored letters indicating where match was found
            var indicatorX = rightX
            val indicatorSpacing = 10

            // Render in reverse order so they appear left-to-right: T S # C
            val indicators = listOf(
                SearchMatchType.CONTENT to Pair("C", 0xFF88CC88.toInt()),  // Green - content
                SearchMatchType.TAG to Pair("#", 0xFFCC88CC.toInt()),      // Purple - tag
                SearchMatchType.SUMMARY to Pair("S", 0xFF88CCCC.toInt()),  // Cyan - summary
                SearchMatchType.TITLE to Pair("T", 0xFFCCCC88.toInt())     // Yellow - title
            )

            for ((type, pair) in indicators) {
                if (type in matchTypes) {
                    indicatorX -= indicatorSpacing
                    val (letter, color) = pair
                    guiGraphics.drawString(font, letter, indicatorX, y, color, false)
                }
            }
        }
    }

    private inner class ItemEntry(
        val item: ItemDefinition
    ) : ContentListEntry() {

        override val contentId: String get() = item.id

        // Cached item slot for rendering in the list
        private val itemSlot = ItemSlotWidget.fromItemDefinition(item, 1, ItemSlotWidget.SlotSize.SMALL)

        override fun renderContent(
            guiGraphics: GuiGraphics,
            mouseX: Int,
            mouseY: Int,
            hovered: Boolean,
            delta: Float
        ) {
            val x = getX()
            val y = getY()
            val entryWidth = getWidth()
            val entryHeight = getHeight()
            val currentId = (currentContent as? PageContent.ItemPage)?.item?.id
            val selected = (pageList?.selected === this) || (currentId == item.id)
            renderBackground(guiGraphics, x, y, entryWidth, entryHeight, hovered, selected)

            // Render item icon
            val slotSize = ItemSlotWidget.SlotSize.SMALL.pixels
            val iconX = x + 6
            val iconY = y + (entryHeight - slotSize) / 2
            itemSlot.render(guiGraphics, iconX, iconY, mouseX, mouseY, renderBackground = false)

            // Title after item icon
            val textX = iconX + slotSize + 4
            guiGraphics.drawString(font, item.name, textX, y + 4, Colors.TEXT, true)

            // Summary in second line
            val summary = item.summary
            if (!summary.isNullOrEmpty()) {
                val maxLen = (entryWidth - textX + x - 8) / 4
                val displayText = if (summary.length > maxLen) {
                    summary.take(maxLen - 3) + "..."
                } else {
                    summary
                }
                guiGraphics.drawString(font, displayText, textX, y + 16, Colors.TEXT_SECONDARY, false)
            }
        }
    }
}
