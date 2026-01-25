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
        var currentX = 0
        var lines = 1

        for (span in spans) {
            val tokens = span.text.split(Regex("(?<= )|(?= )")).filter { it.isNotEmpty() }
            for (token in tokens) {
                val tokenWidth = font.width(token)
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
        var totalHeight = 0

        for (item in items) {
            val indent = item.indent * 16
            val availableWidth = width - indent - 12 // Account for bullet and padding
            var currentX = 0
            var itemLines = 1

            for (span in item.spans) {
                val tokens = span.text.split(Regex("(?<= )|(?= )")).filter { it.isNotEmpty() }
                for (token in tokens) {
                    val tokenWidth = font.width(token)
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
        val availableWidth = width - 10 // Account for left border padding
        var currentX = 0
        var lines = 1

        for (span in spans) {
            val tokens = span.text.split(Regex("(?<= )|(?= )")).filter { it.isNotEmpty() }
            for (token in tokens) {
                val tokenWidth = font.width(token)
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
    val headers: List<String>,
    val rows: List<List<String>>
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
enum class RecipeType {
    CUSTOM,     // Custom factory/machine recipes
    CRAFTING,   // 3x3 crafting grid (optional)
    SMELTING    // Furnace-style smelting (optional)
}

/**
 * Recipe element for displaying crafting/processing recipes.
 */
data class RecipeElement(
    val type: RecipeType,
    val name: String? = null,
    val inputs: List<ItemSpec>,
    val outputs: List<ItemSpec>,
    val metadata: Map<String, String> = emptyMap()
) : MarkdownElement() {
    override val baseHeight: Int = 60 // Base height for recipe box

    override fun calculateHeight(width: Int, font: Font): Int {
        // Header line if name exists
        val headerHeight = if (name != null) font.lineHeight + 8 else 0
        // Input/output row with item slots (18px each + padding)
        val itemRowHeight = 26
        // Metadata lines
        val metadataHeight = metadata.size * (font.lineHeight + 2)
        // Padding
        val padding = 12

        return headerHeight + itemRowHeight + metadataHeight + padding
    }
}
