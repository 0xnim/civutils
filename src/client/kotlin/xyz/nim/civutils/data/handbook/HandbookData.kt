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
    val related: List<String> = emptyList(),
    /** If set, this page represents a vanilla item and will show the item icon in the header */
    val itemId: String? = null,
    /** If set, references a CustomItemDefinition.id for custom server items */
    val customItemId: String? = null
) {
    /** Check if this page represents an item (either vanilla or custom) */
    val isItemPage: Boolean get() = itemId != null || customItemId != null
}

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

/**
 * Represents where a search match was found.
 */
enum class SearchMatchType {
    TITLE,
    TAG,
    SUMMARY,
    CONTENT
}

/**
 * A search result with match information for highlighting.
 */
data class SearchResult(
    val page: HandbookPageMeta,
    val matchTypes: Set<SearchMatchType>,
    val matchSnippet: String? = null
)
