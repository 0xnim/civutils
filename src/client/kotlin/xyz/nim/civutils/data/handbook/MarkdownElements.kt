package xyz.nim.civutils.data.handbook

import net.minecraft.client.gui.Font

/**
 * Sealed class hierarchy for parsed markdown elements.
 * Used for rendering markdown content in the GUI.
 */
sealed class MarkdownElement {
    /** Base estimated height in pixels (used as fallback) */
    abstract val baseHeight: Int

    /** Calculate actual height based on available width and font */
    abstract fun calculateHeight(width: Int, font: Font): Int

    /** Legacy property for backwards compatibility */
    val height: Int get() = baseHeight
}

data class HeadingElement(
    val level: Int,
    val text: String
) : MarkdownElement() {
    override val baseHeight: Int = when (level) {
        1 -> 24
        2 -> 20
        else -> 16
    }

    override fun calculateHeight(width: Int, font: Font): Int {
        // Headings don't wrap, use base height
        return baseHeight
    }
}

data class ParagraphElement(
    val spans: List<TextSpan>
) : MarkdownElement() {
    override val baseHeight: Int = 14

    override fun calculateHeight(width: Int, font: Font): Int {
        val lineHeight = font.lineHeight + 2
        val inlineItemSize = 14 // SlotSize.SMALL
        var currentX = 0
        var lines = 1

        for (span in spans) {
            // Handle inline items (icon only)
            if (span.isItem && span.itemId != null && span.text.isEmpty()) {
                if (currentX + inlineItemSize > width && currentX > 0) {
                    currentX = 0
                    lines++
                }
                currentX += inlineItemSize + 2
                continue
            }

            // Handle item links (icon + text)
            if (span.isItem && span.itemId != null && span.text.isNotEmpty()) {
                val textWidth = font.width(span.text)
                val totalWidth = inlineItemSize + 2 + textWidth
                if (currentX + totalWidth > width && currentX > 0) {
                    currentX = 0
                    lines++
                }
                currentX += totalWidth + 4
                continue
            }

            val tokens = span.text.split(Regex("(?<= )|(?= )")).filter { it.isNotEmpty() }
            for (token in tokens) {
                // Calculate width WITH formatting - bold/italic text is wider
                val formattedToken = buildString {
                    if (span.bold) append("\u00A7l")
                    if (span.italic) append("\u00A7o")
                    append(token)
                }
                val tokenWidth = font.width(formattedToken)
                if (currentX + tokenWidth > width && currentX > 0 && token.isNotBlank()) {
                    currentX = 0
                    lines++
                }
                currentX += tokenWidth
            }
        }

        return lines * lineHeight
    }
}

data class TextSpan(
    val text: String,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val code: Boolean = false,
    val link: String? = null,
    val itemId: String? = null,
    val itemCount: Int = 1
) {
    /** Check if this span represents an inline item reference */
    val isItem: Boolean get() = itemId != null
}

data class ListElement(
    val items: List<ListItem>,
    val ordered: Boolean = false
) : MarkdownElement() {
    override val baseHeight: Int = items.size * 14

    override fun calculateHeight(width: Int, font: Font): Int {
        val lineHeight = font.lineHeight + 2
        val inlineItemSize = 14 // SlotSize.SMALL
        var totalHeight = 0

        for (item in items) {
            val indent = item.indent * 16
            val availableWidth = width - indent - 12 // Account for bullet and padding
            var currentX = 0
            var itemLines = 1

            for (span in item.spans) {
                // Handle inline items (icon only)
                if (span.isItem && span.itemId != null && span.text.isEmpty()) {
                    if (currentX + inlineItemSize > availableWidth && currentX > 0) {
                        currentX = 0
                        itemLines++
                    }
                    currentX += inlineItemSize + 2
                    continue
                }

                // Handle item links (icon + text)
                if (span.isItem && span.itemId != null && span.text.isNotEmpty()) {
                    val textWidth = font.width(span.text)
                    val totalWidth = inlineItemSize + 2 + textWidth
                    if (currentX + totalWidth > availableWidth && currentX > 0) {
                        currentX = 0
                        itemLines++
                    }
                    currentX += totalWidth + 4
                    continue
                }

                val tokens = span.text.split(Regex("(?<= )|(?= )")).filter { it.isNotEmpty() }
                for (token in tokens) {
                    // Calculate width WITH formatting - bold/italic text is wider
                    val formattedToken = buildString {
                        if (span.bold) append("\u00A7l")
                        if (span.italic) append("\u00A7o")
                        append(token)
                    }
                    val tokenWidth = font.width(formattedToken)
                    if (currentX + tokenWidth > availableWidth && currentX > 0 && token.isNotBlank()) {
                        currentX = 0
                        itemLines++
                    }
                    currentX += tokenWidth
                }
            }

            totalHeight += itemLines * lineHeight
        }

        return totalHeight
    }
}

data class ListItem(
    val spans: List<TextSpan>,
    val indent: Int = 0
)

data class CodeBlockElement(
    val code: String,
    val language: String = ""
) : MarkdownElement() {
    override val baseHeight: Int = 12 * (code.lines().size + 2) + 8

    override fun calculateHeight(width: Int, font: Font): Int {
        val lines = code.lines()
        return (lines.size) * (font.lineHeight + 2) + 8
    }
}

data class HorizontalRuleElement(
    override val baseHeight: Int = 12
) : MarkdownElement() {
    override fun calculateHeight(width: Int, font: Font): Int = 12
}

data class BlockQuoteElement(
    val spans: List<TextSpan>
) : MarkdownElement() {
    override val baseHeight: Int = 18

    override fun calculateHeight(width: Int, font: Font): Int {
        val lineHeight = font.lineHeight + 2
        val inlineItemSize = 14 // SlotSize.SMALL
        val availableWidth = width - 10 // Account for left border padding
        var currentX = 0
        var lines = 1

        for (span in spans) {
            // Handle inline items (icon only)
            if (span.isItem && span.itemId != null && span.text.isEmpty()) {
                if (currentX + inlineItemSize > availableWidth && currentX > 0) {
                    currentX = 0
                    lines++
                }
                currentX += inlineItemSize + 2
                continue
            }

            // Handle item links (icon + text)
            if (span.isItem && span.itemId != null && span.text.isNotEmpty()) {
                val textWidth = font.width(span.text)
                val totalWidth = inlineItemSize + 2 + textWidth
                if (currentX + totalWidth > availableWidth && currentX > 0) {
                    currentX = 0
                    lines++
                }
                currentX += totalWidth + 4
                continue
            }

            val tokens = span.text.split(Regex("(?<= )|(?= )")).filter { it.isNotEmpty() }
            for (token in tokens) {
                // Calculate width WITH formatting - bold/italic text is wider
                val formattedToken = buildString {
                    if (span.bold) append("\u00A7l")
                    if (span.italic) append("\u00A7o")
                    append(token)
                }
                val tokenWidth = font.width(formattedToken)
                if (currentX + tokenWidth > availableWidth && currentX > 0 && token.isNotBlank()) {
                    currentX = 0
                    lines++
                }
                currentX += tokenWidth
            }
        }

        return maxOf(baseHeight, lines * lineHeight + 4)
    }
}

data class TableElement(
    val headers: List<List<TextSpan>>,
    val rows: List<List<List<TextSpan>>>
) : MarkdownElement() {
    override val baseHeight: Int = (rows.size + 1) * 16 + 8

    override fun calculateHeight(width: Int, font: Font): Int {
        val rowHeight = font.lineHeight + 6
        return (rows.size + 1) * rowHeight + 2 // +1 for header, +2 for borders
    }
}

/**
 * Represents an item specification with ID and count.
 */
data class ItemSpec(
    val itemId: String,
    val count: Int = 1
)

/**
 * Types of recipes that can be displayed.
 */
enum class MarkdownRecipeType {
    CUSTOM,     // Custom factory/machine recipes
    CRAFTING,   // 3x3 crafting grid (shapeless)
    SHAPED,     // 3x3 crafting grid with specific shape
    SMELTING    // Furnace-style smelting
}

/**
 * Recipe element for displaying crafting/processing recipes.
 *
 * For shaped recipes, the `shape` field contains a 3x3 grid pattern using characters
 * that map to items via the `shapeKey` field. Empty slots use space or underscore.
 * Example: shape = listOf("III", "   ", "   ") with shapeKey = mapOf('I' to ItemSpec("minecraft:iron_ingot"))
 */
data class RecipeElement(
    val type: MarkdownRecipeType,
    val name: String? = null,
    val inputs: List<ItemSpec>,
    val outputs: List<ItemSpec>,
    val metadata: Map<String, String> = emptyMap(),
    /** 3x3 grid pattern for shaped recipes (list of 3 strings, each 3 characters) */
    val shape: List<String>? = null,
    /** Maps pattern characters to item specs */
    val shapeKey: Map<Char, ItemSpec> = emptyMap()
) : MarkdownElement() {
    override val baseHeight: Int = if (type == MarkdownRecipeType.SHAPED && shape != null) 80 else 60

    override fun calculateHeight(width: Int, font: Font): Int {
        // Header line if name exists
        val headerHeight = if (name != null) font.lineHeight + 8 else 0
        // For shaped recipes, we show a 3x3 grid (3 rows of 18px slots + spacing)
        val itemRowHeight = if (type == MarkdownRecipeType.SHAPED && shape != null) {
            3 * 18 + 8 // 3 rows of slots plus spacing
        } else {
            26 // Single row
        }
        // Metadata lines
        val metadataHeight = metadata.size * (font.lineHeight + 2)
        // Padding
        val padding = 12

        return headerHeight + itemRowHeight + metadataHeight + padding
    }
}

/**
 * Displays all unlocks for a specific class level, grouped by type.
 * Shows craftable items, mineable blocks, interaction unlocks, mechanics, and other unlock types with headers.
 */
data class ClassUnlocksElement(
    val className: String,
    val level: Int
) : MarkdownElement() {
    override val baseHeight: Int = 0 // Calculated dynamically based on item count

    override fun calculateHeight(width: Int, font: Font): Int {
        val craftItems = xyz.nim.civutils.models.HandbookModel.getItemsByClassLevel(className, level)
        val mineItems = xyz.nim.civutils.models.HandbookModel.getItemsByMiningClassLevel(className, level)
        val interactItems = xyz.nim.civutils.models.HandbookModel.getItemsByInteractionClassLevel(className, level)
        val mechanics = xyz.nim.civutils.models.HandbookModel.getMechanicsByClassLevel(className, level)

        if (craftItems.isEmpty() && mineItems.isEmpty() && interactItems.isEmpty() && mechanics.isEmpty()) return 0

        val slotSize = 18
        val gap = 4
        val headerHeight = font.lineHeight + 4
        val slotsPerRow = maxOf(1, (width + gap) / (slotSize + gap))

        var totalHeight = 0

        // Craftable items section
        if (craftItems.isNotEmpty()) {
            totalHeight += headerHeight
            val rows = (craftItems.size + slotsPerRow - 1) / slotsPerRow
            totalHeight += rows * (slotSize + gap)
        }

        // Mineable items section
        if (mineItems.isNotEmpty()) {
            totalHeight += headerHeight
            val rows = (mineItems.size + slotsPerRow - 1) / slotsPerRow
            totalHeight += rows * (slotSize + gap)
        }

        // Interaction items section
        if (interactItems.isNotEmpty()) {
            totalHeight += headerHeight
            val rows = (interactItems.size + slotsPerRow - 1) / slotsPerRow
            totalHeight += rows * (slotSize + gap)
        }

        // Mechanics section (text links, one line per mechanic)
        if (mechanics.isNotEmpty()) {
            totalHeight += headerHeight
            totalHeight += mechanics.size * (font.lineHeight + 2)
        }

        return totalHeight
    }
}
