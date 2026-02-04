package xyz.nim.civutils.gui.widgets

import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import xyz.nim.civutils.data.handbook.*
import xyz.nim.civutils.utils.ItemMatcher
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.civutils.gui.widgets.ItemSlotWidget.SlotSize
import xyz.nim.civutils.utils.renderOutline

/**
 * Renders markdown elements to the GUI.
 * Tracks link positions for click handling and hover effects.
 * Supports code block copy functionality.
 */
class MarkdownRenderer {

    private val linkRegions = mutableListOf<LinkRegion>()
    private val codeBlockRegions = mutableListOf<CodeBlockRegion>()
    private val itemSlotManager = ItemSlotManager()

    // Hover state
    private var hoveredLink: LinkRegion? = null
    private var hoveredCodeBlock: CodeBlockRegion? = null

    data class LinkRegion(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val target: String
    )

    data class CodeBlockRegion(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val code: String,
        val buttonX: Int,
        val buttonY: Int,
        val buttonWidth: Int,
        val buttonHeight: Int
    )

    /**
     * Update hover state based on mouse position.
     * Call this each frame before rendering.
     */
    fun updateHover(mouseX: Int, mouseY: Int) {
        hoveredLink = linkRegions.find { region ->
            mouseX >= region.x && mouseX < region.x + region.width &&
                    mouseY >= region.y && mouseY < region.y + region.height
        }

        hoveredCodeBlock = codeBlockRegions.find { region ->
            mouseX >= region.buttonX && mouseX < region.buttonX + region.buttonWidth &&
                    mouseY >= region.buttonY && mouseY < region.buttonY + region.buttonHeight
        }
    }

    /**
     * Check if mouse is hovering over a link (for cursor change).
     */
    fun isHoveringLink(): Boolean = hoveredLink != null

    /**
     * Check if mouse is hovering over a copy button.
     */
    fun isHoveringCopyButton(): Boolean = hoveredCodeBlock != null

    /**
     * Render a markdown element at the given position.
     * Returns the height consumed.
     */
    fun render(
        guiGraphics: GuiGraphics,
        element: MarkdownElement,
        x: Int,
        y: Int,
        width: Int,
        font: Font
    ): Int {
        return when (element) {
            is HeadingElement -> renderHeading(guiGraphics, element, x, y, font)
            is ParagraphElement -> renderParagraph(guiGraphics, element, x, y, width, font)
            is ListElement -> renderList(guiGraphics, element, x, y, width, font)
            is CodeBlockElement -> renderCodeBlock(guiGraphics, element, x, y, width, font)
            is BlockQuoteElement -> renderBlockQuote(guiGraphics, element, x, y, width, font)
            is HorizontalRuleElement -> renderHorizontalRule(guiGraphics, x, y, width)
            is TableElement -> renderTable(guiGraphics, element, x, y, width, font)
            is RecipeElement -> renderRecipe(guiGraphics, element, x, y, width, font)
            is ClassUnlocksElement -> renderClassUnlocks(guiGraphics, element, x, y, width, font)
        }
    }

    private fun renderHeading(
        guiGraphics: GuiGraphics,
        element: HeadingElement,
        x: Int,
        y: Int,
        font: Font
    ): Int {
        val color = when (element.level) {
            1 -> NlibTheme.ACCENT
            2 -> NlibTheme.TEXT_PRIMARY
            else -> NlibTheme.TEXT_PRIMARY
        }

        // Use bold formatting for headings (scaling is problematic in newer MC versions)
        val prefix = when (element.level) {
            1 -> "\u00A7l\u00A7n"  // Bold + Underline for H1
            2 -> "\u00A7l"        // Bold for H2
            else -> ""
        }
        val text = "$prefix${element.text}\u00A7r"

        guiGraphics.drawString(font, text, x, y, color, true)

        // Draw underline for H1
        if (element.level == 1) {
            // Calculate width WITH bold formatting - bold text is wider
            val textWidth = font.width("\u00A7l${element.text}")
            guiGraphics.fill(x, y + font.lineHeight + 2, x + textWidth, y + font.lineHeight + 3, color)
        }

        return element.baseHeight
    }

    private fun renderParagraph(
        guiGraphics: GuiGraphics,
        element: ParagraphElement,
        x: Int,
        y: Int,
        width: Int,
        font: Font
    ): Int {
        return renderSpans(guiGraphics, element.spans, x, y, width, font)
    }

    private fun renderSpans(
        guiGraphics: GuiGraphics,
        spans: List<TextSpan>,
        startX: Int,
        startY: Int,
        maxWidth: Int,
        font: Font,
        mouseX: Int = 0,
        mouseY: Int = 0
    ): Int {
        var currentX = startX
        var currentY = startY
        val lineHeight = font.lineHeight + 2

        for (span in spans) {
            // Handle inline item references (icon only, no text)
            if (span.isItem && span.itemId != null && span.text.isEmpty()) {
                val slotSize = SlotSize.SMALL.pixels // 14px for inline items

                // Wrap if needed
                if (currentX + slotSize > startX + maxWidth && currentX > startX) {
                    currentX = startX
                    currentY += lineHeight
                }

                // Create item slot (uses createItemSlot for proper database lookup and navigation)
                val slot = createItemSlot(span.itemId, span.itemCount, SlotSize.SMALL)
                val slotY = currentY + (font.lineHeight - slotSize) / 2
                slot.render(guiGraphics, currentX, slotY, mouseX, mouseY, renderBackground = true)
                itemSlotManager.addSlot(slot)
                itemSlotManager.recordBounds(slot)

                currentX += slotSize + 2 // Small gap after item
                continue
            }

            // Handle item links (icon + text, both clickable)
            if (span.isItem && span.itemId != null && span.text.isNotEmpty()) {
                val slotSize = SlotSize.SMALL.pixels // 14px for inline items
                val isHovered = span.link != null && hoveredLink?.target == span.link

                // Calculate total width (icon + gap + text)
                val textWidth = font.width(span.text)
                val totalWidth = slotSize + 2 + textWidth

                // Wrap if needed
                if (currentX + totalWidth > startX + maxWidth && currentX > startX) {
                    currentX = startX
                    currentY += lineHeight
                }

                // Render item icon
                val slot = createItemSlot(span.itemId, span.itemCount, SlotSize.SMALL)
                val slotY = currentY + (font.lineHeight - slotSize) / 2
                slot.render(guiGraphics, currentX, slotY, mouseX, mouseY, renderBackground = true)
                itemSlotManager.addSlot(slot)
                itemSlotManager.recordBounds(slot)

                val iconEndX = currentX + slotSize + 2

                // Render text after icon
                val textColor = if (isHovered) LINK_HOVER_COLOR else NlibTheme.ACCENT
                guiGraphics.drawString(font, span.text, iconEndX, currentY, textColor, true)

                // Draw underline for hovered item links
                if (isHovered) {
                    guiGraphics.fill(
                        iconEndX,
                        currentY + font.lineHeight,
                        iconEndX + textWidth,
                        currentY + font.lineHeight + 1,
                        LINK_HOVER_COLOR
                    )
                }

                // Track link region for both icon and text
                if (span.link != null) {
                    linkRegions.add(
                        LinkRegion(
                            currentX,
                            currentY,
                            totalWidth,
                            maxOf(slotSize, font.lineHeight),
                            span.link
                        )
                    )
                }

                currentX += totalWidth + 4 // Gap after item link
                continue
            }

            val isHovered = span.link != null && hoveredLink?.target == span.link

            // Determine color
            val color = when {
                span.link != null && isHovered -> LINK_HOVER_COLOR
                span.link != null -> NlibTheme.ACCENT
                span.code -> 0xFFAAFFAA.toInt()
                else -> NlibTheme.TEXT_PRIMARY
            }

            // Split into words but preserve spaces by using regex
            val tokens = span.text.split(Regex("(?<= )|(?= )")).filter { it.isNotEmpty() }

            for (token in tokens) {
                // Build formatted text (need this first to calculate correct width)
                val formattedToken = buildString {
                    if (span.bold) append("\u00A7l")
                    if (span.italic) append("\u00A7o")
                    append(token)
                    if (span.bold || span.italic) append("\u00A7r")
                }

                // Calculate width WITH formatting - bold text is wider than regular text
                val tokenWidth = font.width(formattedToken)

                // Word wrap (but not for spaces at start of line)
                if (currentX + tokenWidth > startX + maxWidth && currentX > startX && token.isNotBlank()) {
                    currentX = startX
                    currentY += lineHeight
                }

                // Code background
                if (span.code && token.isNotBlank()) {
                    guiGraphics.fill(
                        currentX - 1,
                        currentY - 1,
                        currentX + tokenWidth + 1,
                        currentY + font.lineHeight + 1,
                        0x40000000
                    )
                }

                guiGraphics.drawString(font, formattedToken, currentX, currentY, color, !span.code)

                // Draw underline for hovered links
                if (span.link != null && isHovered && token.isNotBlank()) {
                    guiGraphics.fill(
                        currentX,
                        currentY + font.lineHeight,
                        currentX + tokenWidth,
                        currentY + font.lineHeight + 1,
                        LINK_HOVER_COLOR
                    )
                }

                // Track link region
                if (span.link != null && token.isNotBlank()) {
                    linkRegions.add(
                        LinkRegion(
                            currentX,
                            currentY,
                            tokenWidth,
                            font.lineHeight,
                            span.link
                        )
                    )
                }

                currentX += tokenWidth
            }
        }

        return currentY - startY + lineHeight
    }

    private fun renderList(
        guiGraphics: GuiGraphics,
        element: ListElement,
        x: Int,
        y: Int,
        width: Int,
        font: Font
    ): Int {
        var currentY = y

        for ((index, item) in element.items.withIndex()) {
            val indent = item.indent * 16
            val bulletX = x + indent

            val bullet = if (element.ordered) "${index + 1}." else "•"
            guiGraphics.drawString(font, bullet, bulletX, currentY, NlibTheme.TEXT_SECONDARY, false)

            val spansHeight = renderSpans(guiGraphics, item.spans, bulletX + 12, currentY, width - indent - 12, font)
            currentY += maxOf(font.lineHeight + 2, spansHeight)
        }

        return currentY - y
    }

    private fun renderCodeBlock(
        guiGraphics: GuiGraphics,
        element: CodeBlockElement,
        x: Int,
        y: Int,
        width: Int,
        font: Font
    ): Int {
        val lines = element.code.lines()
        val blockHeight = (lines.size) * (font.lineHeight + 2) + 8

        // Background
        guiGraphics.fill(x, y, x + width, y + blockHeight, 0x80000000.toInt())

        // Border
        guiGraphics.renderOutline(x, y, width, blockHeight, 0x40FFFFFF)

        // Copy button
        val buttonWidth = 40
        val buttonHeight = 12
        val buttonX = x + width - buttonWidth - 4
        val buttonY = y + 4
        val isButtonHovered = hoveredCodeBlock?.code == element.code

        // Button background
        val buttonBgColor = if (isButtonHovered) 0x60FFFFFF else 0x30FFFFFF
        guiGraphics.fill(buttonX, buttonY, buttonX + buttonWidth, buttonY + buttonHeight, buttonBgColor)
        guiGraphics.renderOutline(buttonX, buttonY, buttonWidth, buttonHeight, 0x60FFFFFF)

        // Button text
        val buttonText = "Copy"
        val textX = buttonX + (buttonWidth - font.width(buttonText)) / 2
        val textY = buttonY + (buttonHeight - font.lineHeight) / 2
        guiGraphics.drawString(font, buttonText, textX, textY, NlibTheme.TEXT_PRIMARY, false)

        // Track code block region for copy functionality
        codeBlockRegions.add(
            CodeBlockRegion(
                x, y, width, blockHeight, element.code,
                buttonX, buttonY, buttonWidth, buttonHeight
            )
        )

        // Code lines
        var lineY = y + 4
        for (line in lines) {
            guiGraphics.drawString(font, line, x + 8, lineY, 0xFFAAFFAA.toInt(), false)
            lineY += font.lineHeight + 2
        }

        return blockHeight
    }

    private fun renderBlockQuote(
        guiGraphics: GuiGraphics,
        element: BlockQuoteElement,
        x: Int,
        y: Int,
        width: Int,
        font: Font
    ): Int {
        // Left border
        guiGraphics.fill(x, y, x + 3, y + font.lineHeight + 4, NlibTheme.ACCENT)

        // Content
        val spansHeight = renderSpans(guiGraphics, element.spans, x + 10, y + 2, width - 10, font)
        return maxOf(element.baseHeight, spansHeight + 4)
    }

    private fun renderTable(
        guiGraphics: GuiGraphics,
        element: TableElement,
        x: Int,
        y: Int,
        width: Int,
        font: Font
    ): Int {
        val rowHeight = font.lineHeight + 6
        val numColumns = element.headers.size
        if (numColumns == 0) return 0

        val colWidth = width / numColumns
        var currentY = y

        // Draw header background
        guiGraphics.fill(x, currentY, x + width, currentY + rowHeight, 0x40FFFFFF)

        // Draw headers (make all spans bold)
        for ((colIndex, headerSpans) in element.headers.withIndex()) {
            val colX = x + colIndex * colWidth + 4
            val maxCellWidth = colWidth - 8
            val boldSpans = headerSpans.map { it.copy(bold = true) }
            renderSpans(guiGraphics, boldSpans, colX, currentY + 3, maxCellWidth, font)
        }
        currentY += rowHeight

        // Draw separator line
        guiGraphics.fill(x, currentY - 1, x + width, currentY, NlibTheme.TEXT_SECONDARY)

        // Draw rows
        for ((rowIndex, row) in element.rows.withIndex()) {
            // Alternate row background
            if (rowIndex % 2 == 1) {
                guiGraphics.fill(x, currentY, x + width, currentY + rowHeight, 0x20FFFFFF)
            }

            for ((colIndex, cellSpans) in row.withIndex()) {
                if (colIndex < numColumns) {
                    val colX = x + colIndex * colWidth + 4
                    val maxCellWidth = colWidth - 8
                    renderSpans(guiGraphics, cellSpans, colX, currentY + 3, maxCellWidth, font)
                }
            }
            currentY += rowHeight
        }

        // Draw border
        guiGraphics.renderOutline(x, y, width, currentY - y, NlibTheme.TEXT_SECONDARY)

        // Draw column separators
        for (colIndex in 1 until numColumns) {
            val lineX = x + colIndex * colWidth
            guiGraphics.fill(lineX, y, lineX + 1, currentY, 0x40FFFFFF)
        }

        return currentY - y
    }

    private fun renderRecipe(
        guiGraphics: GuiGraphics,
        element: RecipeElement,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int = 0,
        mouseY: Int = 0
    ): Int {
        // Use shaped rendering for shaped recipes with a valid shape
        if (element.type == MarkdownRecipeType.SHAPED && element.shape != null && element.shapeKey.isNotEmpty()) {
            return renderShapedRecipe(guiGraphics, element, x, y, width, font, mouseX, mouseY)
        }

        var currentY = y
        val padding = 6
        val slotSize = SlotSize.NORMAL.pixels // 18px for recipe items

        // Calculate total height
        val headerHeight = if (element.name != null) font.lineHeight + 4 else 0
        val itemRowHeight = slotSize + 8
        val metadataHeight = element.metadata.size * (font.lineHeight + 2)
        val totalHeight = headerHeight + itemRowHeight + metadataHeight + padding * 2

        // Background box
        guiGraphics.fill(x, y, x + width, y + totalHeight, 0x40000000)
        guiGraphics.renderOutline(x, y, width, totalHeight, 0x60FFFFFF)

        currentY += padding

        // Recipe name header
        if (element.name != null) {
            guiGraphics.drawString(font, "\u00A7l${element.name}\u00A7r", x + padding, currentY, NlibTheme.ACCENT, true)
            currentY += font.lineHeight + 4
        }

        // Input items
        var itemX = x + padding
        for (input in element.inputs) {
            val slot = createItemSlot(input.itemId, input.count, SlotSize.NORMAL)
            slot.render(guiGraphics, itemX, currentY, mouseX, mouseY, renderBackground = true)
            itemSlotManager.addSlot(slot)
            itemSlotManager.recordBounds(slot)
            itemX += slotSize + 4
        }

        // Arrow
        val arrowX = itemX + 4
        guiGraphics.drawString(font, "\u2192", arrowX, currentY + (slotSize - font.lineHeight) / 2, NlibTheme.TEXT_PRIMARY, false)
        itemX = arrowX + font.width("\u2192") + 8

        // Output items
        for (output in element.outputs) {
            val slot = createItemSlot(output.itemId, output.count, SlotSize.NORMAL)
            slot.render(guiGraphics, itemX, currentY, mouseX, mouseY, renderBackground = true)
            itemSlotManager.addSlot(slot)
            itemSlotManager.recordBounds(slot)
            itemX += slotSize + 4
        }

        currentY += slotSize + 4

        // Metadata (time, fuel, etc.)
        for ((key, value) in element.metadata) {
            val metaText = when (key) {
                "time" -> "\u23F1 $value"  // Timer icon
                "fuel" -> "\uD83D\uDD25 $value"  // Fire icon (may not render)
                else -> "$key: $value"
            }
            guiGraphics.drawString(font, metaText, x + padding, currentY, NlibTheme.TEXT_SECONDARY, false)
            currentY += font.lineHeight + 2
        }

        return totalHeight
    }

    /**
     * Render a shaped crafting recipe with a 3x3 grid.
     */
    private fun renderShapedRecipe(
        guiGraphics: GuiGraphics,
        element: RecipeElement,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int = 0,
        mouseY: Int = 0
    ): Int {
        var currentY = y
        val padding = 6
        val slotSize = SlotSize.NORMAL.pixels // 18px
        val slotGap = 2

        // Calculate total height
        val headerHeight = if (element.name != null) font.lineHeight + 4 else 0
        val gridHeight = 3 * slotSize + 2 * slotGap // 3 rows with gaps
        val metadataHeight = element.metadata.size * (font.lineHeight + 2)
        val totalHeight = headerHeight + gridHeight + metadataHeight + padding * 2 + 4

        // Background box
        guiGraphics.fill(x, y, x + width, y + totalHeight, 0x40000000)
        guiGraphics.renderOutline(x, y, width, totalHeight, 0x60FFFFFF)

        currentY += padding

        // Recipe name header
        if (element.name != null) {
            guiGraphics.drawString(font, "\u00A7l${element.name}\u00A7r", x + padding, currentY, NlibTheme.ACCENT, true)
            currentY += font.lineHeight + 4
        }

        val gridStartX = x + padding
        val gridStartY = currentY

        // Render 3x3 crafting grid
        val shape = element.shape!!
        for (row in 0 until 3) {
            val rowPattern = shape.getOrElse(row) { "   " }
            for (col in 0 until 3) {
                val slotX = gridStartX + col * (slotSize + slotGap)
                val slotY = gridStartY + row * (slotSize + slotGap)

                // Draw slot background
                guiGraphics.fill(slotX, slotY, slotX + slotSize, slotY + slotSize, 0x40000000)
                guiGraphics.renderOutline(slotX, slotY, slotSize, slotSize, 0x40FFFFFF)

                // Get item for this position
                val char = rowPattern.getOrElse(col) { ' ' }
                if (char != ' ' && char != '_') {
                    val itemSpec = element.shapeKey[char]
                    if (itemSpec != null) {
                        val slot = createItemSlot(itemSpec.itemId, itemSpec.count, SlotSize.NORMAL)
                        slot.render(guiGraphics, slotX, slotY, mouseX, mouseY, renderBackground = false)
                        itemSlotManager.addSlot(slot)
                        itemSlotManager.recordBounds(slot)
                    }
                }
            }
        }

        // Arrow after grid
        val gridEndX = gridStartX + 3 * (slotSize + slotGap)
        val arrowX = gridEndX + 8
        val arrowY = gridStartY + gridHeight / 2 - font.lineHeight / 2
        guiGraphics.drawString(font, "\u2192", arrowX, arrowY, NlibTheme.TEXT_PRIMARY, false)

        // Output items
        var itemX = arrowX + font.width("\u2192") + 12
        val outputY = gridStartY + gridHeight / 2 - slotSize / 2
        for (output in element.outputs) {
            val slot = createItemSlot(output.itemId, output.count, SlotSize.NORMAL)
            slot.render(guiGraphics, itemX, outputY, mouseX, mouseY, renderBackground = true)
            itemSlotManager.addSlot(slot)
            itemSlotManager.recordBounds(slot)
            itemX += slotSize + 4
        }

        currentY = gridStartY + gridHeight + 4

        // Metadata (time, fuel, etc.)
        for ((key, value) in element.metadata) {
            val metaText = when (key) {
                "time" -> "\u23F1 $value"  // Timer icon
                "fuel" -> "\uD83D\uDD25 $value"  // Fire icon (may not render)
                else -> "$key: $value"
            }
            guiGraphics.drawString(font, metaText, x + padding, currentY, NlibTheme.TEXT_SECONDARY, false)
            currentY += font.lineHeight + 2
        }

        return totalHeight
    }

    private fun renderHorizontalRule(
        guiGraphics: GuiGraphics,
        x: Int,
        y: Int,
        width: Int
    ): Int {
        guiGraphics.fill(x, y + 5, x + width, y + 6, NlibTheme.TEXT_SECONDARY)
        return 12
    }

    private fun renderClassUnlocks(
        guiGraphics: GuiGraphics,
        element: ClassUnlocksElement,
        x: Int,
        y: Int,
        width: Int,
        font: Font,
        mouseX: Int = 0,
        mouseY: Int = 0
    ): Int {
        val craftItems = xyz.nim.civutils.models.HandbookModel.getItemsByClassLevel(element.className, element.level)
        val mineItems = xyz.nim.civutils.models.HandbookModel.getItemsByMiningClassLevel(element.className, element.level)
        val interactItems = xyz.nim.civutils.models.HandbookModel.getItemsByInteractionClassLevel(element.className, element.level)
        val mechanics = xyz.nim.civutils.models.HandbookModel.getMechanicsByClassLevel(element.className, element.level)

        if (craftItems.isEmpty() && mineItems.isEmpty() && interactItems.isEmpty() && mechanics.isEmpty()) {
            return 0
        }

        val slotSize = SlotSize.NORMAL.pixels // 18px
        val gap = 4
        val headerHeight = font.lineHeight + 4
        val slotsPerRow = maxOf(1, (width + gap) / (slotSize + gap))

        var currentY = y
        var totalHeight = 0

        // Render craftable items section
        if (craftItems.isNotEmpty()) {
            guiGraphics.drawString(font, "Craftable:", x, currentY, NlibTheme.TEXT_SECONDARY, false)
            currentY += headerHeight
            totalHeight += headerHeight

            val sectionHeight = renderItemGrid(guiGraphics, craftItems, x, currentY, slotsPerRow, slotSize, gap, mouseX, mouseY)
            currentY += sectionHeight
            totalHeight += sectionHeight
        }

        // Render mineable items section
        if (mineItems.isNotEmpty()) {
            guiGraphics.drawString(font, "Mineable:", x, currentY, NlibTheme.TEXT_SECONDARY, false)
            currentY += headerHeight
            totalHeight += headerHeight

            val sectionHeight = renderItemGrid(guiGraphics, mineItems, x, currentY, slotsPerRow, slotSize, gap, mouseX, mouseY)
            currentY += sectionHeight
            totalHeight += sectionHeight
        }

        // Render interaction items section
        if (interactItems.isNotEmpty()) {
            guiGraphics.drawString(font, "Interactions:", x, currentY, NlibTheme.TEXT_SECONDARY, false)
            currentY += headerHeight
            totalHeight += headerHeight

            val sectionHeight = renderItemGrid(guiGraphics, interactItems, x, currentY, slotsPerRow, slotSize, gap, mouseX, mouseY)
            currentY += sectionHeight
            totalHeight += sectionHeight
        }

        // Render mechanics section
        if (mechanics.isNotEmpty()) {
            guiGraphics.drawString(font, "Mechanics:", x, currentY, NlibTheme.TEXT_SECONDARY, false)
            currentY += headerHeight
            totalHeight += headerHeight

            val lineHeight = font.lineHeight + 2
            for (mechanic in mechanics) {
                // Find the class-specific description for this unlock
                val unlock = mechanic.classUnlocks.find {
                    it.className == element.className && it.level == element.level
                }
                val description = unlock?.description?.let { " ($it)" } ?: ""

                // Check if this mechanic link is hovered
                val linkTarget = mechanic.pageId ?: mechanic.id
                val isHovered = hoveredLink?.target == linkTarget
                val linkColor = if (isHovered) LINK_HOVER_COLOR else NlibTheme.ACCENT

                // Render mechanic name as a clickable link
                val text = "• ${mechanic.name}$description"
                guiGraphics.drawString(font, "• ", x, currentY, NlibTheme.TEXT_PRIMARY, false)
                val bulletWidth = font.width("• ")

                val nameText = mechanic.name
                guiGraphics.drawString(font, nameText, x + bulletWidth, currentY, linkColor, true)
                val nameWidth = font.width(nameText)

                // Draw underline for hovered links
                if (isHovered) {
                    guiGraphics.fill(
                        x + bulletWidth,
                        currentY + font.lineHeight,
                        x + bulletWidth + nameWidth,
                        currentY + font.lineHeight + 1,
                        LINK_HOVER_COLOR
                    )
                }

                // Track link region for the mechanic name
                linkRegions.add(
                    LinkRegion(
                        x + bulletWidth,
                        currentY,
                        nameWidth,
                        font.lineHeight,
                        linkTarget
                    )
                )

                // Render description after the link
                if (description.isNotEmpty()) {
                    guiGraphics.drawString(font, description, x + bulletWidth + nameWidth, currentY, NlibTheme.TEXT_SECONDARY, false)
                }

                currentY += lineHeight
                totalHeight += lineHeight
            }
        }

        return totalHeight
    }

    private fun renderItemGrid(
        guiGraphics: GuiGraphics,
        items: List<ItemDefinition>,
        x: Int,
        y: Int,
        slotsPerRow: Int,
        slotSize: Int,
        gap: Int,
        mouseX: Int,
        mouseY: Int
    ): Int {
        var currentX = x
        var currentY = y
        var itemsInRow = 0

        for (item in items) {
            if (itemsInRow >= slotsPerRow) {
                currentX = x
                currentY += slotSize + gap
                itemsInRow = 0
            }

            val slot = ItemSlotWidget.fromItemDefinition(item, 1, SlotSize.NORMAL)
            slot.render(guiGraphics, currentX, currentY, mouseX, mouseY, renderBackground = true)
            itemSlotManager.addSlot(slot)
            itemSlotManager.recordBounds(slot)

            currentX += slotSize + gap
            itemsInRow++
        }

        val rows = (items.size + slotsPerRow - 1) / slotsPerRow
        return rows * (slotSize + gap)
    }

    /**
     * Get the link target at the given screen coordinates, if any.
     */
    fun getLinkAt(x: Int, y: Int): String? {
        return linkRegions.find { region ->
            x >= region.x && x < region.x + region.width &&
                    y >= region.y && y < region.y + region.height
        }?.target
    }

    /**
     * Get the code at the given screen coordinates if clicking a copy button.
     */
    fun getCodeBlockAt(x: Int, y: Int): String? {
        return codeBlockRegions.find { region ->
            x >= region.buttonX && x < region.buttonX + region.buttonWidth &&
                    y >= region.buttonY && y < region.buttonY + region.buttonHeight
        }?.code
    }

    /**
     * Clear tracked link, code block, and item regions. Call before rendering a frame.
     */
    fun clearRegions() {
        linkRegions.clear()
        codeBlockRegions.clear()
        itemSlotManager.clear()
    }

    /**
     * @deprecated Use clearRegions() instead
     */
    fun clearLinkRegions() = clearRegions()

    /**
     * Render item tooltips for hovered items. Call after content rendering.
     */
    fun renderItemTooltips(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int) {
        itemSlotManager.renderHoveredTooltip(guiGraphics, mouseX, mouseY)
    }

    /**
     * Get the item ID at the given screen coordinates, if any.
     * Used for click handling to navigate to item pages.
     */
    fun getItemAt(x: Int, y: Int): String? {
        return itemSlotManager.getItemAt(x, y)
    }

    /**
     * Check if mouse is hovering over an item slot.
     */
    fun isHoveringItem(mouseX: Int, mouseY: Int): Boolean {
        return itemSlotManager.getItemAt(mouseX, mouseY) != null
    }

    /**
     * Register an item region manually (for external item rendering like "Used In" sections).
     * Note: This only registers click regions. For tooltips, use addItemSlot() instead.
     */
    fun registerItemRegion(x: Int, y: Int, width: Int, height: Int, itemId: String) {
        itemSlotManager.registerRegion(x, y, width, height, itemId)
    }

    /**
     * Add an ItemSlotWidget for tooltip rendering and click handling.
     * Use this for externally rendered item slots (like relationship sections).
     */
    fun addItemSlot(slot: ItemSlotWidget) {
        itemSlotManager.addSlot(slot)
        itemSlotManager.recordBounds(slot)
    }

    /**
     * Create an ItemSlotWidget from an item ID.
     * Supports both vanilla IDs (minecraft:iron_ingot) and custom item IDs (iron_plate).
     * Preserves the original ID for click-through navigation.
     */
    private fun createItemSlot(itemId: String, count: Int, size: SlotSize): ItemSlotWidget {
        // Check if this is a custom item ID (doesn't contain ':')
        if (!itemId.contains(':')) {
            // First check the new items database
            val itemDef = xyz.nim.civutils.models.HandbookModel.getItem(itemId)
            if (itemDef != null) {
                return ItemSlotWidget.fromItemDefinition(itemDef, count, size)
            }

            // Fall back to old custom items system
            val customDef = ItemMatcher.getDefinition(itemId)
            if (customDef != null) {
                return ItemSlotWidget(
                    itemId = customDef.filters.baseItem ?: "",
                    count = count,
                    size = size,
                    customItemDef = customDef,
                    navigationId = itemId  // Keep original ID for navigation
                )
            }
        }
        // Fall back to vanilla item
        return ItemSlotWidget(itemId, count, size)
    }

    companion object {
        private const val LINK_HOVER_COLOR = 0xFF88CCFF.toInt()
    }
}
