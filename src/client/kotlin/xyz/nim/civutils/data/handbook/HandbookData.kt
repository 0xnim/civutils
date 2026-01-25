package xyz.nim.civutils.data.handbook

/**
 * Represents a handbook category for grouping pages.
 */
data class HandbookCategory(
    val id: String,
    val name: String,
    val icon: String = "book",
    val order: Int = 0
)

/**
 * Represents metadata for a handbook page.
 */
data class HandbookPageMeta(
    val id: String,
    val title: String,
    val file: String,
    val category: String,
    val tags: List<String> = emptyList(),
    val order: Int = 0,
    val summary: String = "",
    val related: List<String> = emptyList()
)

/**
 * Represents a loaded handbook page with content.
 */
data class HandbookPage(
    val meta: HandbookPageMeta,
    val content: String,
    val renderedContent: List<MarkdownElement>
)

/**
 * The handbook index manifest.
 */
data class HandbookIndex(
    val version: Int = 1,
    val defaultPage: String = "",
    val categories: List<HandbookCategory> = emptyList(),
    val pages: List<HandbookPageMeta> = emptyList()
)
