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

/**
 * Unified search result that can represent either a page or an item,
 * with a relevance score for sorting mixed results.
 *
 * Lower score = higher relevance (sorted ascending).
 */
sealed class ScoredSearchResult(open val score: Int) {
    data class PageResult(
        val page: HandbookPageMeta,
        val matchTypes: Set<SearchMatchType>,
        val matchSnippet: String?,
        override val score: Int
    ) : ScoredSearchResult(score)

    data class ItemResult(
        val item: ItemDefinition,
        override val score: Int
    ) : ScoredSearchResult(score)
}

/**
 * Relevance score constants for unified search ranking.
 * Lower values = higher priority in results.
 */
object SearchRelevance {
    const val ITEM_NAME_EXACT = 0
    const val PAGE_TITLE_EXACT = 1
    const val ITEM_NAME_STARTS_WITH = 2
    const val PAGE_TITLE_STARTS_WITH = 3
    const val ITEM_NAME_CONTAINS = 4
    const val PAGE_TITLE_CONTAINS = 5
    const val ITEM_TAG_OR_SUMMARY = 6
    const val PAGE_TAG_OR_SUMMARY = 7
    const val PAGE_CONTENT_ONLY = 8
}
