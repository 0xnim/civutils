package xyz.nim.civutils.gui.screens

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.data.handbook.*
import xyz.nim.civutils.gui.widgets.Colors
import xyz.nim.civutils.gui.widgets.MarkdownRenderer
import xyz.nim.civutils.models.HandbookModel
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.lib.ui.components.NlibListWidget
import kotlin.math.max

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
    private var searchQuery = ""

    // Content
    private var currentPage: HandbookPage? = null
    private var contentScroll = 0.0  // Float for smooth scrolling
    private var targetScroll = 0.0   // Target for animation
    private var maxScroll = 0

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

    // Content renderer
    private val markdownRenderer = MarkdownRenderer()

    // Content area dimensions (for renderer)
    private var contentStartX = 0
    private var contentStartY = 0
    private var contentAreaWidth = 0
    private var contentAreaHeight = 0

    companion object {
        private const val SCROLL_SPEED = 0.3  // Smooth scroll interpolation factor
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

        // Search box
        searchBox = EditBox(font, leftPanelX + 58, 25, leftPanelWidth - 58, 18, Component.literal(""))
        searchBox?.setHint(Component.literal("Search..."))
        searchBox?.setResponder { query ->
            searchQuery = query
            refreshPageList()
        }
        addWidget(searchBox)

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

        // Populate categories
        val allCategory = HandbookCategory("__all__", "All Pages", "book", -1)
        categoryList?.addEntryToList(CategoryEntry(allCategory))
        for (category in HandbookModel.getCategories()) {
            categoryList?.addEntryToList(CategoryEntry(category))
        }
        addWidget(categoryList)

        // Page list
        val pageListY = contentY + listHeaderOffset + categoryListHeight + 8
        val pageListHeight = contentHeight - listHeaderOffset - categoryListHeight - 8
        pageList = PageListWidget(
            minecraft!!, leftPanelWidth, pageListHeight,
            pageListY, 28
        ) { pageMeta ->
            loadPage(pageMeta.id)
        }
        pageList?.setX(leftPanelX)
        addWidget(pageList)

        refreshPageList()

        // Load initial page
        val pageToLoad = initialPageId ?: HandbookModel.getIndex().defaultPage
        if (pageToLoad.isNotEmpty()) {
            loadPage(pageToLoad)
        }
    }

    private fun refreshPageList() {
        pageList?.clearEntries()

        val pages = if (searchQuery.isNotBlank()) {
            HandbookModel.searchPages(searchQuery)
        } else if (selectedCategory != null && selectedCategory?.id != "__all__") {
            HandbookModel.getPagesInCategory(selectedCategory!!.id)
        } else {
            HandbookModel.getPages()
        }

        for (page in pages.sortedBy { it.order }) {
            pageList?.addEntryToList(PageEntry(page))
        }
    }

    private fun loadPage(pageId: String, addToHistory: Boolean = true) {
        currentPage = HandbookModel.getPage(pageId)
        contentScroll = 0.0
        targetScroll = 0.0

        if (addToHistory) {
            HandbookModel.navigateTo(pageId)
        }

        // Update navigation button states
        backButton?.active = HandbookModel.canGoBack()
        forwardButton?.active = HandbookModel.canGoForward()

        // Calculate max scroll with dynamic heights
        currentPage?.let { page ->
            var totalHeight = 0
            var isFirst = true
            for (element in page.renderedContent) {
                // Top margin for headings
                if (!isFirst && element is HeadingElement) {
                    totalHeight += when (element.level) {
                        1 -> 16
                        2 -> 12
                        else -> 8
                    }
                }
                // Use dynamic height calculation
                totalHeight += element.calculateHeight(contentAreaWidth, font)
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
    }

    override fun tick() {
        super.tick()

        // Smooth scroll animation
        if (kotlin.math.abs(contentScroll - targetScroll) > 0.5) {
            contentScroll += (targetScroll - contentScroll) * SCROLL_SPEED
        } else {
            contentScroll = targetScroll
        }
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
        val page = currentPage

        if (page == null) {
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

        // Calculate adjusted mouse Y for scroll offset (for hover detection)
        val adjustedMouseY = if (mouseX in contentStartX..(contentStartX + contentAreaWidth) &&
            mouseY in contentStartY..(contentStartY + contentAreaHeight)
        ) {
            (mouseY + contentScroll - contentStartY).toInt() + contentStartY
        } else {
            -1 // Mouse not in content area
        }

        var y = contentStartY - contentScroll.toInt()
        var isFirst = true

        for (element in page.renderedContent) {
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

            // Calculate dynamic height
            val elementHeight = element.calculateHeight(contentAreaWidth, font)

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

        guiGraphics.disableScissor()

        // Update hover state after rendering (link regions are now populated)
        markdownRenderer.updateHover(mouseX, adjustedMouseY)

        // Scrollbar
        renderScrollbar(guiGraphics, mouseX, mouseY)
    }

    private fun renderScrollbar(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        if (maxScroll <= 0) return

        scrollbarX = rightPanelX + rightPanelWidth - SCROLLBAR_WIDTH - 2
        scrollbarTrackY = contentStartY
        scrollbarTrackHeight = contentAreaHeight

        scrollbarThumbHeight = max(20, (contentAreaHeight * contentAreaHeight) / (contentAreaHeight + maxScroll))
        scrollbarThumbY = scrollbarTrackY + ((contentScroll.toInt() * (scrollbarTrackHeight - scrollbarThumbHeight)) / maxScroll)

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

        val rightTitle = currentPage?.meta?.title ?: "Select a page"
        guiGraphics.drawString(font, rightTitle, rightPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)

        // Breadcrumb
        currentPage?.let { page ->
            val category = HandbookModel.getCategories().find { it.id == page.meta.category }
            if (category != null) {
                val breadcrumb = "${category.name} > ${page.meta.title}"
                guiGraphics.drawString(font, breadcrumb, rightPanelX + 8, contentY + 18, NlibTheme.TEXT_SECONDARY, false)
            }
        }

        // Copy feedback tooltip
        if (markdownRenderer.isHoveringCopyButton()) {
            // Could show a tooltip here
        }
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
            // Update target for smooth scrolling
            targetScroll = (targetScroll - (verticalAmount * 30)).coerceIn(0.0, maxScroll.toDouble())
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
                    targetScroll = (clickRatio * maxScroll).coerceIn(0.0, maxScroll.toDouble())
                    return true
                }
            }

            // Handle content area clicks
            val page = currentPage
            if (page != null) {
                if (mouseX >= contentStartX && mouseX < contentStartX + contentAreaWidth &&
                    mouseY >= contentStartY && mouseY < contentStartY + contentAreaHeight
                ) {
                    val adjustedY = (mouseY + contentScroll - contentStartY).toInt() + contentStartY

                    // Check for code block copy button click
                    val code = markdownRenderer.getCodeBlockAt(mouseX.toInt(), adjustedY)
                    if (code != null) {
                        copyToClipboard(code)
                        toastManager.info("Copied to clipboard")
                        return true
                    }

                    // Check for link click
                    val link = markdownRenderer.getLinkAt(mouseX.toInt(), adjustedY)
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

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, dragX: Double, dragY: Double): Boolean {
        if (isDraggingScrollbar && maxScroll > 0) {
            val newThumbY = mouseY - scrollbarDragOffset
            val scrollRatio = (newThumbY - scrollbarTrackY) / (scrollbarTrackHeight - scrollbarThumbHeight)
            targetScroll = (scrollRatio * maxScroll).coerceIn(0.0, maxScroll.toDouble())
            contentScroll = targetScroll // Immediate update while dragging
            return true
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY)
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
                // Internal page link
                if (HandbookModel.getPage(link) != null) {
                    loadPage(link)
                } else {
                    toastManager.error("Page not found: $link")
                }
            }
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

            val pageCount = if (category.id == "__all__") {
                HandbookModel.getPages().size
            } else {
                HandbookModel.getPagesInCategory(category.id).size
            }

            val text = "${category.name} ($pageCount)"
            guiGraphics.drawString(font, text, x + 8, y + 6, Colors.TEXT, true)
        }
    }

    private inner class PageListWidget(
        client: Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int,
        private val onSelect: (HandbookPageMeta) -> Unit
    ) : NlibListWidget<PageEntry>(client, width, height, y, itemHeight) {

        override fun setSelected(entry: PageEntry?) {
            super.setSelected(entry)
            entry?.page?.let { onSelect(it) }
        }
    }

    private inner class PageEntry(val page: HandbookPageMeta) : NlibListWidget.Entry<PageEntry>() {
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
            val selected = (pageList?.selected === this) || (currentPage?.meta?.id == page.id)
            renderBackground(guiGraphics, x, y, entryWidth, entryHeight, hovered, selected)

            guiGraphics.drawString(font, page.title, x + 8, y + 4, Colors.TEXT, true)

            // Summary preview
            if (page.summary.isNotEmpty()) {
                val maxLen = (entryWidth - 16) / 4
                val summary = if (page.summary.length > maxLen) {
                    page.summary.take(maxLen - 3) + "..."
                } else {
                    page.summary
                }
                guiGraphics.drawString(font, summary, x + 8, y + 16, Colors.TEXT_SECONDARY, false)
            }
        }
    }
}
