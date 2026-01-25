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

        // Parse cells from a table row
        fun parseCells(line: String): List<String> {
            return line.trim()
                .removeSurrounding("|")
                .split("|")
                .map { it.trim() }
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
     * Parse inline formatting (bold, italic, code, links).
     */
    private fun parseInlineText(text: String): List<TextSpan> {
        val spans = mutableListOf<TextSpan>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            val patterns = listOf(
                // Links: [text](url)
                Regex("^\\[([^\\]]+)\\]\\(([^)]+)\\)") to { m: MatchResult ->
                    TextSpan(m.groupValues[1], link = m.groupValues[2])
                },
                // Bold + Italic: ***text*** or ___text___
                Regex("^\\*\\*\\*(.+?)\\*\\*\\*") to { m: MatchResult ->
                    TextSpan(m.groupValues[1], bold = true, italic = true)
                },
                // Bold: **text** or __text__
                Regex("^\\*\\*(.+?)\\*\\*") to { m: MatchResult ->
                    TextSpan(m.groupValues[1], bold = true)
                },
                Regex("^__(.+?)__") to { m: MatchResult ->
                    TextSpan(m.groupValues[1], bold = true)
                },
                // Italic: *text* or _text_
                Regex("^\\*([^*]+)\\*") to { m: MatchResult ->
                    TextSpan(m.groupValues[1], italic = true)
                },
                Regex("^_([^_]+)_") to { m: MatchResult ->
                    TextSpan(m.groupValues[1], italic = true)
                },
                // Inline code: `text`
                Regex("^`([^`]+)`") to { m: MatchResult ->
                    TextSpan(m.groupValues[1], code = true)
                }
            )

            var matched = false
            for ((pattern, handler) in patterns) {
                val match = pattern.find(remaining)
                if (match != null) {
                    if (match.range.first > 0) {
                        spans.add(TextSpan(remaining.substring(0, match.range.first)))
                    }
                    val span = handler(match)
                    remaining = remaining.substring(match.range.last + 1)
                    spans.add(span)

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
                    spans.add(TextSpan(remaining.first().toString()))
                    remaining = remaining.drop(1)
                }
            }
        }

        return mergeAdjacentPlainText(spans)
    }

    /**
     * Merge adjacent plain text spans for efficiency.
     */
    private fun mergeAdjacentPlainText(spans: List<TextSpan>): List<TextSpan> {
        if (spans.isEmpty()) return spans

        val result = mutableListOf<TextSpan>()
        var current = spans.first()

        for (span in spans.drop(1)) {
            if (!current.bold && !current.italic && !current.code && current.link == null &&
                !span.bold && !span.italic && !span.code && span.link == null
            ) {
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
