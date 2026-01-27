package xyz.nim.civutils.utils

import xyz.nim.civutils.data.handbook.*

/**
 * Simple markdown parser for handbook content.
 * Supports: headings, paragraphs, bold, italic, code, lists, links, blockquotes, horizontal rules.
 */
class MarkdownParser {

    fun parse(markdown: String): List<MarkdownElement> {
        val elements = mutableListOf<MarkdownElement>()
        val lines = stripFrontMatter(markdown).lines()

        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            when {
                trimmed.isEmpty() -> {
                    i++
                }

                trimmed.startsWith("#") -> {
                    val match = Regex("^(#{1,6})\\s+(.+)$").find(trimmed)
                    if (match != null) {
                        val level = match.groupValues[1].length
                        val text = match.groupValues[2]
                        elements.add(HeadingElement(level, text))
                    }
                    i++
                }

                trimmed.matches(Regex("^[-*_]{3,}$")) -> {
                    elements.add(HorizontalRuleElement())
                    i++
                }

                trimmed.startsWith("```recipe") -> {
                    // Recipe block
                    val recipeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        recipeLines.add(lines[i])
                        i++
                    }
                    val recipe = parseRecipeBlock(recipeLines)
                    if (recipe != null) {
                        elements.add(recipe)
                    }
                    i++
                }

                trimmed.startsWith("```") -> {
                    val language = trimmed.removePrefix("```").trim()
                    val codeLines = mutableListOf<String>()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith("```")) {
                        codeLines.add(lines[i])
                        i++
                    }
                    elements.add(CodeBlockElement(codeLines.joinToString("\n"), language))
                    i++
                }

                trimmed.startsWith(">") -> {
                    val quoteText = trimmed.removePrefix(">").trim()
                    elements.add(BlockQuoteElement(parseInlineText(quoteText)))
                    i++
                }

                // Table: starts with | and has multiple |
                trimmed.startsWith("|") && trimmed.count { it == '|' } >= 2 -> {
                    val tableLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().startsWith("|")) {
                        tableLines.add(lines[i].trim())
                        i++
                    }
                    val table = parseTable(tableLines)
                    if (table != null) {
                        elements.add(table)
                    }
                }

                trimmed.matches(Regex("^[-*+]\\s+.+")) -> {
                    val items = mutableListOf<ListItem>()
                    while (i < lines.size && lines[i].trim().matches(Regex("^[-*+]\\s+.+"))) {
                        val indent = lines[i].takeWhile { it == ' ' || it == '\t' }.length / 2
                        val text = lines[i].trim().replaceFirst(Regex("^[-*+]\\s+"), "")
                        items.add(ListItem(parseInlineText(text), indent))
                        i++
                    }
                    elements.add(ListElement(items, ordered = false))
                }

                trimmed.matches(Regex("^\\d+\\.\\s+.+")) -> {
                    val items = mutableListOf<ListItem>()
                    while (i < lines.size && lines[i].trim().matches(Regex("^\\d+\\.\\s+.+"))) {
                        val indent = lines[i].takeWhile { it == ' ' || it == '\t' }.length / 2
                        val text = lines[i].trim().replaceFirst(Regex("^\\d+\\.\\s+"), "")
                        items.add(ListItem(parseInlineText(text), indent))
                        i++
                    }
                    elements.add(ListElement(items, ordered = true))
                }

                else -> {
                    val paragraphLines = mutableListOf<String>()
                    while (i < lines.size && lines[i].trim().isNotEmpty() &&
                        !lines[i].trim().startsWith("#") &&
                        !lines[i].trim().startsWith("```") &&
                        !lines[i].trim().startsWith(">") &&
                        !lines[i].trim().matches(Regex("^[-*+]\\s+.+")) &&
                        !lines[i].trim().matches(Regex("^\\d+\\.\\s+.+")) &&
                        !lines[i].trim().matches(Regex("^[-*_]{3,}$"))
                    ) {
                        paragraphLines.add(lines[i].trim())
                        i++
                    }
                    if (paragraphLines.isNotEmpty()) {
                        val text = paragraphLines.joinToString(" ")
                        elements.add(ParagraphElement(parseInlineText(text)))
                    }
                }
            }
        }

        return elements
    }

    /**
     * Parse a markdown table from lines.
     */
    private fun parseTable(lines: List<String>): TableElement? {
        if (lines.size < 2) return null

        // Parse cells from a table row, with inline markdown support
        fun parseCells(line: String): List<List<TextSpan>> {
            return line.trim()
                .removeSurrounding("|")
                .split("|")
                .map { parseInlineText(it.trim()) }
        }

        val headers = parseCells(lines[0])

        // Skip the separator line (|---|---|)
        val dataStartIndex = if (lines.size > 1 && lines[1].contains("-")) 2 else 1

        val rows = lines.drop(dataStartIndex).map { parseCells(it) }

        return TableElement(headers, rows)
    }

    /**
     * Strip YAML front matter from markdown content.
     */
    private fun stripFrontMatter(markdown: String): String {
        val lines = markdown.lines()
        if (lines.firstOrNull()?.trim() == "---") {
            val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
            if (endIndex >= 0) {
                return lines.drop(endIndex + 2).joinToString("\n")
            }
        }
        return markdown
    }

    /**
     * Parse a recipe block from lines inside ```recipe ... ```.
     * Format is YAML-like:
     *   type: custom
     *   name: Ore Smelter
     *   inputs:
     *     - minecraft:iron_ore|64
     *   outputs:
     *     - minecraft:iron_ingot|64
     *   time: 30m
     *
     * For shaped recipes:
     *   type: shaped
     *   name: Iron Plate
     *   shape:
     *     III
     *     ___
     *     ___
     *   key:
     *     I: minecraft:iron_ingot
     *   output: minecraft:iron_ingot
     */
    private fun parseRecipeBlock(lines: List<String>): RecipeElement? {
        var type = MarkdownRecipeType.CUSTOM
        var name: String? = null
        val inputs = mutableListOf<ItemSpec>()
        val outputs = mutableListOf<ItemSpec>()
        val metadata = mutableMapOf<String, String>()
        val shape = mutableListOf<String>()
        val shapeKey = mutableMapOf<Char, ItemSpec>()

        var currentList: MutableList<ItemSpec>? = null
        var parsingShape = false
        var parsingKey = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Shape pattern lines (after "shape:")
            if (parsingShape && !trimmed.contains(':')) {
                // Shape pattern line - preserve exactly 3 characters, padding with spaces
                val patternLine = trimmed.take(3).padEnd(3, ' ')
                shape.add(patternLine)
                if (shape.size >= 3) parsingShape = false
                continue
            }

            // Key mapping lines (after "key:")
            if (parsingKey && trimmed.length >= 2 && trimmed[1] == ':') {
                val keyChar = trimmed[0]
                val itemSpec = parseItemSpec(trimmed.substring(2).trim())
                if (itemSpec != null) {
                    shapeKey[keyChar] = itemSpec
                }
                continue
            }

            // List item (under inputs: or outputs:)
            if (trimmed.startsWith("-") && currentList != null) {
                val spec = trimmed.removePrefix("-").trim()
                val itemSpec = parseItemSpec(spec)
                if (itemSpec != null) {
                    currentList.add(itemSpec)
                }
                continue
            }

            // Key: value
            val colonIndex = trimmed.indexOf(':')
            if (colonIndex > 0) {
                val key = trimmed.substring(0, colonIndex).trim().lowercase()
                val value = trimmed.substring(colonIndex + 1).trim()

                parsingShape = false
                parsingKey = false

                when (key) {
                    "type" -> {
                        type = when (value.lowercase()) {
                            "crafting" -> MarkdownRecipeType.CRAFTING
                            "shaped" -> MarkdownRecipeType.SHAPED
                            "smelting" -> MarkdownRecipeType.SMELTING
                            else -> MarkdownRecipeType.CUSTOM
                        }
                        currentList = null
                    }
                    "name" -> {
                        name = value
                        currentList = null
                    }
                    "shape" -> {
                        parsingShape = true
                        currentList = null
                    }
                    "key" -> {
                        parsingKey = true
                        currentList = null
                    }
                    "inputs", "input" -> {
                        currentList = inputs
                        // Single-line input: input: minecraft:iron_ore|64
                        if (value.isNotEmpty()) {
                            parseItemSpec(value)?.let { inputs.add(it) }
                            currentList = null
                        }
                    }
                    "outputs", "output" -> {
                        currentList = outputs
                        // Single-line output: output: minecraft:iron_ingot
                        if (value.isNotEmpty()) {
                            parseItemSpec(value)?.let { outputs.add(it) }
                            currentList = null
                        }
                    }
                    else -> {
                        // Generic metadata (time, fuel, etc.)
                        metadata[key] = value
                        currentList = null
                    }
                }
            }
        }

        // For shaped recipes, convert shape+key to inputs
        if (type == MarkdownRecipeType.SHAPED && shape.isNotEmpty() && shapeKey.isNotEmpty()) {
            for (row in shape) {
                for (char in row) {
                    if (char != ' ' && char != '_') {
                        shapeKey[char]?.let { inputs.add(it) }
                    }
                }
            }
        }

        // Must have at least inputs or outputs (or a shape for shaped recipes)
        if (inputs.isEmpty() && outputs.isEmpty() && shape.isEmpty()) return null

        return RecipeElement(
            type = type,
            name = name,
            inputs = inputs,
            outputs = outputs,
            metadata = metadata,
            shape = if (shape.isNotEmpty()) shape else null,
            shapeKey = shapeKey
        )
    }

    /**
     * Parse an item specification like "minecraft:iron_ore|64" or "minecraft:iron_ore".
     */
    private fun parseItemSpec(spec: String): ItemSpec? {
        val trimmed = spec.trim()
        if (trimmed.isEmpty()) return null

        val parts = trimmed.split("|", limit = 2)
        val itemId = parts[0].trim()
        val count = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 1

        return ItemSpec(itemId, count)
    }

    /**
     * Parse inline formatting (bold, italic, code, links, items).
     * Supports nested formatting like [**bold link**](url) or **[link](url)**.
     */
    private fun parseInlineText(text: String): List<TextSpan> {
        val spans = mutableListOf<TextSpan>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            val patterns = listOf(
                // Inline item: [[minecraft:iron_ore]] or [[minecraft:iron_ore|64]]
                Regex("^\\[\\[([^\\]|]+)(\\|\\d+)?\\]\\]") to { m: MatchResult ->
                    val itemId = m.groupValues[1]
                    val countStr = m.groupValues[2].removePrefix("|")
                    val count = countStr.toIntOrNull() ?: 1
                    listOf(TextSpan("", itemId = itemId, itemCount = count))
                },
                // Links with inner formatting: [**bold**](url), [*italic*](url), [***bold italic***](url)
                Regex("^\\[\\*\\*\\*(.+?)\\*\\*\\*\\]\\(([^)]+)\\)") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], bold = true, italic = true, link = m.groupValues[2]))
                },
                Regex("^\\[\\*\\*(.+?)\\*\\*\\]\\(([^)]+)\\)") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], bold = true, link = m.groupValues[2]))
                },
                Regex("^\\[\\*(.+?)\\*\\]\\(([^)]+)\\)") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], italic = true, link = m.groupValues[2]))
                },
                // Links: [text](url)
                Regex("^\\[([^\\]]+)\\]\\(([^)]+)\\)") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], link = m.groupValues[2]))
                },
                // Bold/italic wrapping a link: **[text](url)**, *[text](url)*
                Regex("^\\*\\*\\*\\[([^\\]]+)\\]\\(([^)]+)\\)\\*\\*\\*") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], bold = true, italic = true, link = m.groupValues[2]))
                },
                Regex("^\\*\\*\\[([^\\]]+)\\]\\(([^)]+)\\)\\*\\*") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], bold = true, link = m.groupValues[2]))
                },
                Regex("^\\*\\[([^\\]]+)\\]\\(([^)]+)\\)\\*") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], italic = true, link = m.groupValues[2]))
                },
                // Bold + Italic: ***text*** or ___text___
                Regex("^\\*\\*\\*(.+?)\\*\\*\\*") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], bold = true, italic = true))
                },
                // Bold: **text** or __text__
                Regex("^\\*\\*(.+?)\\*\\*") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], bold = true))
                },
                Regex("^__(.+?)__") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], bold = true))
                },
                // Italic: *text* or _text_
                Regex("^\\*([^*]+)\\*") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], italic = true))
                },
                Regex("^_([^_]+)_") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], italic = true))
                },
                // Inline code: `text`
                Regex("^`([^`]+)`") to { m: MatchResult ->
                    listOf(TextSpan(m.groupValues[1], code = true))
                }
            )

            var matched = false
            for ((pattern, handler) in patterns) {
                val match = pattern.find(remaining)
                if (match != null) {
                    if (match.range.first > 0) {
                        spans.add(TextSpan(remaining.substring(0, match.range.first)))
                    }
                    val newSpans = handler(match)
                    remaining = remaining.substring(match.range.last + 1)
                    spans.addAll(newSpans)

                    // Add trailing space as separate plain span to prevent space loss
                    // (formatting codes can interfere with space rendering)
                    if (remaining.startsWith(" ")) {
                        spans.add(TextSpan(" "))
                        remaining = remaining.substring(1)
                    }
                    matched = true
                    break
                }
            }

            if (!matched) {
                val nextSpecial = remaining.indexOfAny(charArrayOf('*', '_', '`', '['))
                if (nextSpecial > 0) {
                    spans.add(TextSpan(remaining.substring(0, nextSpecial)))
                    remaining = remaining.substring(nextSpecial)
                } else if (nextSpecial < 0) {
                    spans.add(TextSpan(remaining))
                    remaining = ""
                } else {
                    // Check for [[ (item syntax) vs [ (link syntax)
                    if (remaining.startsWith("[[")) {
                        // Not a match for item regex, so consume one [ and continue
                        spans.add(TextSpan("["))
                        remaining = remaining.drop(1)
                    } else {
                        spans.add(TextSpan(remaining.first().toString()))
                        remaining = remaining.drop(1)
                    }
                }
            }
        }

        return mergeAdjacentPlainText(spans)
    }

    /**
     * Merge adjacent plain text spans for efficiency.
     * Does not merge spans with formatting, links, or item references.
     */
    private fun mergeAdjacentPlainText(spans: List<TextSpan>): List<TextSpan> {
        if (spans.isEmpty()) return spans

        val result = mutableListOf<TextSpan>()
        var current = spans.first()

        for (span in spans.drop(1)) {
            // Only merge if both are plain text (no formatting, links, or items)
            val currentIsPlain = !current.bold && !current.italic && !current.code &&
                current.link == null && current.itemId == null
            val spanIsPlain = !span.bold && !span.italic && !span.code &&
                span.link == null && span.itemId == null

            if (currentIsPlain && spanIsPlain) {
                current = current.copy(text = current.text + span.text)
            } else {
                result.add(current)
                current = span
            }
        }
        result.add(current)

        return result
    }
}
