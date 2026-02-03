package xyz.nim.civutils.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.model.Model
import xyz.nim.civutils.data.handbook.*
import xyz.nim.civutils.utils.ItemMatcher
import xyz.nim.civutils.utils.MarkdownParser
import xyz.nim.civutils.utils.MdxParser
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Model for loading and managing handbook content.
 * Supports bundled content with server-specific overrides.
 */
object HandbookModel : Model() {

    private const val HANDBOOK_RESOURCE_PATH = "assets/civutils/handbook"
    private const val INDEX_FILE = "index.json"

    private val configDir: Path = FabricLoader.getInstance().configDir
        .resolve(CivutilsMod.MOD_ID)
        .resolve("handbook")

    private val gson: Gson = GsonBuilder().create()
    private val markdownParser = MarkdownParser()

    // Cached content
    private var bundledIndex: HandbookIndex? = null
    private var serverIndex: HandbookIndex? = null
    private var mergedIndex: HandbookIndex? = null

    // Items database
    private var itemsIndex: ItemsIndex? = null
    private val itemCache = mutableMapOf<String, ItemDefinition>()

    private val pageCache = mutableMapOf<String, HandbookPage>()
    private var currentServerHash: String? = null

    // Search index: pageId -> plain text content (for full-text search)
    private val contentIndex = mutableMapOf<String, String>()
    private var indexBuilt = false

    // Navigation history
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    override fun reset() {
        serverIndex = null
        mergedIndex = null
        pageCache.clear()
        itemCache.clear()
        contentIndex.clear()
        indexBuilt = false
        currentServerHash = null
        history.clear()
        historyIndex = -1
    }

    override fun onActivate() {
        loadBundledContent()
    }

    @Subscribe
    fun onTick(event: ClientTickEvent) {
        val mc = Minecraft.getInstance()
        if (mc.level == null) return

        val serverHash = getServerHash()
        if (serverHash != currentServerHash) {
            currentServerHash = serverHash
            loadServerContent(serverHash)
            rebuildMergedIndex()
        }
    }

    // === Content Loading ===

    private fun loadBundledContent() {
        try {
            // Load index.json for categories and default page only
            val indexStream = javaClass.classLoader
                .getResourceAsStream("$HANDBOOK_RESOURCE_PATH/$INDEX_FILE")

            val baseIndex = if (indexStream != null) {
                gson.fromJson(InputStreamReader(indexStream), HandbookIndex::class.java)
            } else {
                HandbookIndex()
            }

            // Scan for .mdx page files and build pages list from frontmatter
            val pages = loadPagesFromMdx()

            bundledIndex = HandbookIndex(
                version = baseIndex.version,
                defaultPage = baseIndex.defaultPage,
                categories = baseIndex.categories,
                pages = pages.sortedWith(compareBy({ it.category }, { it.order }))
            )

            CivutilsMod.logger.info("Loaded bundled handbook: ${bundledIndex?.pages?.size ?: 0} pages from MDX files")
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load bundled handbook", e)
            bundledIndex = HandbookIndex()
        }

        // Load custom item definitions (legacy)
        loadCustomItems()

        // Load structured items database
        loadItemsDatabase()
    }

    /**
     * Scan for .mdx page files and parse their frontmatter to build the pages list.
     */
    private fun loadPagesFromMdx(): List<HandbookPageMeta> {
        val pages = mutableListOf<HandbookPageMeta>()

        try {
            // Load pages manifest which lists all page files
            val manifestStream = javaClass.classLoader
                .getResourceAsStream("$HANDBOOK_RESOURCE_PATH/pages-manifest.json")

            if (manifestStream != null) {
                data class PagesManifest(val files: List<String>)
                val manifest = gson.fromJson(InputStreamReader(manifestStream), PagesManifest::class.java)

                for (filePath in manifest.files) {
                    val fullPath = "$HANDBOOK_RESOURCE_PATH/$filePath"
                    val stream = javaClass.classLoader.getResourceAsStream(fullPath)
                    if (stream == null) {
                        CivutilsMod.logger.warn("Page file not found: $fullPath")
                        continue
                    }

                    val content = stream.bufferedReader().readText()
                    val pageMeta = MdxParser.parsePageMdx(content, filePath)
                    if (pageMeta != null) {
                        pages.add(pageMeta)
                    } else {
                        CivutilsMod.logger.warn("Failed to parse page MDX: $fullPath")
                    }
                }
            } else {
                CivutilsMod.logger.debug("No pages-manifest.json found, falling back to index.json pages")
                // Fall back to index.json pages if no manifest
                val indexStream = javaClass.classLoader
                    .getResourceAsStream("$HANDBOOK_RESOURCE_PATH/$INDEX_FILE")
                if (indexStream != null) {
                    val index = gson.fromJson(InputStreamReader(indexStream), HandbookIndex::class.java)
                    return index.pages
                }
            }
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load pages from MDX", e)
        }

        return pages
    }

    private fun loadItemsDatabase() {
        // Try loading from MDX files first (new format)
        if (loadItemsFromMdx()) {
            return
        }

        // Fall back to items.json (legacy format)
        loadItemsFromJson()
    }

    /**
     * Load items from MDX files using items-manifest.json.
     * Returns true if successful, false if manifest not found.
     */
    private fun loadItemsFromMdx(): Boolean {
        try {
            val manifestStream = javaClass.classLoader
                .getResourceAsStream("$HANDBOOK_RESOURCE_PATH/items-manifest.json") ?: return false

            data class CategoryEntry(val folder: String, val files: List<String>)
            data class Manifest(val version: Int, val categories: List<CategoryEntry>)

            val manifest = gson.fromJson(InputStreamReader(manifestStream), Manifest::class.java)

            val items = mutableListOf<ItemDefinition>()

            for (category in manifest.categories) {
                for (file in category.files) {
                    val mdxPath = "$HANDBOOK_RESOURCE_PATH/items/${category.folder}/$file"
                    val mdxStream = javaClass.classLoader.getResourceAsStream(mdxPath)
                    if (mdxStream == null) {
                        CivutilsMod.logger.warn("MDX file not found: $mdxPath")
                        continue
                    }

                    val content = mdxStream.bufferedReader().readText()
                    val item = MdxParser.parseItemMdx(content)
                    if (item != null) {
                        items.add(item)
                    } else {
                        CivutilsMod.logger.warn("Failed to parse MDX file: $mdxPath")
                    }
                }
            }

            itemsIndex = ItemsIndex(
                version = manifest.version,
                items = items
            )
            CivutilsMod.logger.info("Loaded ${items.size} items from MDX files")

            // Load item definitions into ItemMatcher for NBT matching
            registerCustomItems()
            return true

        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load items from MDX files", e)
            return false
        }
    }

    /**
     * Load items from legacy items.json format.
     */
    private fun loadItemsFromJson() {
        try {
            val itemsStream = javaClass.classLoader
                .getResourceAsStream("$HANDBOOK_RESOURCE_PATH/items.json")

            if (itemsStream != null) {
                itemsIndex = gson.fromJson(
                    InputStreamReader(itemsStream),
                    ItemsIndex::class.java
                )
                CivutilsMod.logger.info("Loaded ${itemsIndex?.items?.size ?: 0} items from items.json (legacy)")

                registerCustomItems()
            } else {
                CivutilsMod.logger.debug("No items database found")
                itemsIndex = ItemsIndex()
            }
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load items database", e)
            itemsIndex = ItemsIndex()
        }
    }

    /**
     * Register custom items with ItemMatcher for NBT-based matching.
     */
    private fun registerCustomItems() {
        itemsIndex?.getCustomItems()?.let { customItems ->
            val definitions = customItems.mapNotNull { item ->
                item.filters?.let { filters ->
                    CustomItemDefinition(item.id, item.id, filters)
                }
            }
            if (definitions.isNotEmpty()) {
                ItemMatcher.loadDefinitions(definitions)
                CivutilsMod.logger.info("Loaded ${definitions.size} custom item filters")
            }
        }
    }

    private fun loadCustomItems() {
        try {
            val itemsStream = javaClass.classLoader
                .getResourceAsStream("$HANDBOOK_RESOURCE_PATH/custom-items.json")

            if (itemsStream != null) {
                val customItemsIndex = gson.fromJson(
                    InputStreamReader(itemsStream),
                    CustomItemsIndex::class.java
                )
                ItemMatcher.loadDefinitions(customItemsIndex.items)
                CivutilsMod.logger.info("Loaded ${customItemsIndex.items.size} custom item definitions")
            } else {
                CivutilsMod.logger.debug("No custom-items.json found")
            }
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load custom items", e)
        }
    }

    private fun loadServerContent(serverHash: String) {
        val serverDir = configDir.resolve(serverHash)
        val indexFile = serverDir.resolve(INDEX_FILE)

        if (Files.exists(indexFile)) {
            try {
                serverIndex = gson.fromJson(
                    Files.readString(indexFile),
                    HandbookIndex::class.java
                )
                CivutilsMod.logger.info("Loaded server handbook override: ${serverIndex?.pages?.size ?: 0} pages")
            } catch (e: Exception) {
                CivutilsMod.logger.error("Failed to load server handbook", e)
                serverIndex = null
            }
        } else {
            serverIndex = null
        }
    }

    private fun rebuildMergedIndex() {
        val bundled = bundledIndex ?: HandbookIndex()
        val server = serverIndex

        if (server == null) {
            mergedIndex = bundled
            return
        }

        // Merge categories (server overrides bundled)
        val categoryMap = bundled.categories.associateBy { it.id }.toMutableMap()
        server.categories.forEach { categoryMap[it.id] = it }

        // Merge pages (server overrides bundled)
        val pageMap = bundled.pages.associateBy { it.id }.toMutableMap()
        server.pages.forEach { pageMap[it.id] = it }

        mergedIndex = HandbookIndex(
            version = maxOf(bundled.version, server.version),
            defaultPage = server.defaultPage.ifEmpty { bundled.defaultPage },
            categories = categoryMap.values.sortedBy { it.order },
            pages = pageMap.values.sortedBy { it.order }
        )

        pageCache.clear()
        contentIndex.clear()
        indexBuilt = false
    }

    /**
     * Build the search index by loading all page content.
     * Called lazily on first search.
     */
    private fun buildSearchIndex() {
        if (indexBuilt) return

        for (page in getPages()) {
            val content = loadPageContent(page)
            if (content != null) {
                // Store plain text version for searching (strip markdown)
                contentIndex[page.id] = stripMarkdownForSearch(content)
            }
        }
        indexBuilt = true
        CivutilsMod.logger.info("Built search index for ${contentIndex.size} pages")
    }

    /**
     * Strip markdown syntax for plain text search.
     */
    private fun stripMarkdownForSearch(markdown: String): String {
        return markdown
            // Remove YAML front matter
            .replace(Regex("^---[\\s\\S]*?---\\s*"), "")
            // Remove code blocks
            .replace(Regex("```[\\s\\S]*?```"), " ")
            // Remove inline code
            .replace(Regex("`[^`]+`"), " ")
            // Remove links but keep text
            .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
            // Remove images
            .replace(Regex("!\\[([^]]*)]\\([^)]+\\)"), "$1")
            // Remove heading markers
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            // Remove bold/italic markers
            .replace(Regex("[*_]{1,3}"), "")
            // Remove horizontal rules
            .replace(Regex("^[-*_]{3,}$", RegexOption.MULTILINE), "")
            // Remove blockquote markers
            .replace(Regex("^>\\s*", RegexOption.MULTILINE), "")
            // Remove list markers
            .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")
            // Normalize whitespace
            .replace(Regex("\\s+"), " ")
            .trim()
            .lowercase()
    }

    // === Page Access ===

    fun getIndex(): HandbookIndex = mergedIndex ?: bundledIndex ?: HandbookIndex()

    fun getCategories(): List<HandbookCategory> = getIndex().categories

    fun getPages(): List<HandbookPageMeta> = getIndex().pages

    fun getPagesInCategory(categoryId: String): List<HandbookPageMeta> {
        return getPages().filter { it.category == categoryId }
    }

    /**
     * Search pages with full-text search and match information.
     * Returns SearchResult objects with match type and snippet.
     */
    fun searchPages(query: String): List<SearchResult> {
        if (query.isBlank()) {
            return getPages().map { SearchResult(it, emptySet()) }
        }

        // Build index on first search
        buildSearchIndex()

        val lowerQuery = query.lowercase()
        val results = mutableListOf<SearchResult>()

        for (page in getPages()) {
            val matchTypes = mutableSetOf<SearchMatchType>()
            var snippet: String? = null

            // Check title
            if (page.title.lowercase().contains(lowerQuery)) {
                matchTypes.add(SearchMatchType.TITLE)
            }

            // Check tags
            if (page.tags.any { it.lowercase().contains(lowerQuery) }) {
                matchTypes.add(SearchMatchType.TAG)
            }

            // Check summary
            if (page.summary.lowercase().contains(lowerQuery)) {
                matchTypes.add(SearchMatchType.SUMMARY)
            }

            // Check content (full-text search)
            val content = contentIndex[page.id]
            if (content != null && content.contains(lowerQuery)) {
                matchTypes.add(SearchMatchType.CONTENT)

                // Extract snippet around match
                if (snippet == null) {
                    snippet = extractSnippet(content, lowerQuery)
                }
            }

            if (matchTypes.isNotEmpty()) {
                results.add(SearchResult(page, matchTypes, snippet))
            }
        }

        // Sort: title matches first, then by page order
        return results.sortedWith(
            compareByDescending<SearchResult> { SearchMatchType.TITLE in it.matchTypes }
                .thenBy { it.page.order }
        )
    }

    /**
     * Unified search across both pages and items with relevance-based scoring.
     * Returns a single list sorted by relevance score (lower = more relevant).
     *
     * Scoring priority:
     * - Exact item name match (0)
     * - Exact page title match (1)
     * - Item name starts with query (2)
     * - Page title starts with query (3)
     * - Item name contains query (4)
     * - Page title contains query (5)
     * - Item tag/summary match (6)
     * - Page tag/summary match (7)
     * - Page content-only match (8)
     */
    fun unifiedSearch(query: String): List<ScoredSearchResult> {
        if (query.isBlank()) {
            // Return all items and pages by order
            val results = mutableListOf<ScoredSearchResult>()
            for (page in getPages().sortedBy { it.order }) {
                results.add(ScoredSearchResult.PageResult(page, emptySet(), null, page.order))
            }
            for (item in (itemsIndex?.items ?: emptyList()).sortedBy { it.order }) {
                results.add(ScoredSearchResult.ItemResult(item, item.order))
            }
            return results.sortedBy { it.score }
        }

        buildSearchIndex()
        val lowerQuery = query.lowercase()
        val results = mutableListOf<ScoredSearchResult>()

        // Score and add items
        for (item in itemsIndex?.items ?: emptyList()) {
            val lowerName = item.name.lowercase()
            val score = when {
                lowerName == lowerQuery -> SearchRelevance.ITEM_NAME_EXACT
                lowerName.startsWith(lowerQuery) -> SearchRelevance.ITEM_NAME_STARTS_WITH
                lowerName.contains(lowerQuery) -> SearchRelevance.ITEM_NAME_CONTAINS
                item.summary?.lowercase()?.contains(lowerQuery) == true -> SearchRelevance.ITEM_TAG_OR_SUMMARY
                item.tags?.any { it.lowercase().contains(lowerQuery) } == true -> SearchRelevance.ITEM_TAG_OR_SUMMARY
                item.id.lowercase().contains(lowerQuery) -> SearchRelevance.ITEM_TAG_OR_SUMMARY
                else -> null
            }
            if (score != null) {
                results.add(ScoredSearchResult.ItemResult(item, score))
            }
        }

        // Score and add pages
        for (page in getPages()) {
            val matchTypes = mutableSetOf<SearchMatchType>()
            var snippet: String? = null
            val lowerTitle = page.title.lowercase()

            // Check title
            val titleMatch = when {
                lowerTitle == lowerQuery -> SearchRelevance.PAGE_TITLE_EXACT
                lowerTitle.startsWith(lowerQuery) -> SearchRelevance.PAGE_TITLE_STARTS_WITH
                lowerTitle.contains(lowerQuery) -> SearchRelevance.PAGE_TITLE_CONTAINS
                else -> null
            }
            if (titleMatch != null) {
                matchTypes.add(SearchMatchType.TITLE)
            }

            // Check tags
            val tagMatch = page.tags.any { it.lowercase().contains(lowerQuery) }
            if (tagMatch) {
                matchTypes.add(SearchMatchType.TAG)
            }

            // Check summary
            val summaryMatch = page.summary.lowercase().contains(lowerQuery)
            if (summaryMatch) {
                matchTypes.add(SearchMatchType.SUMMARY)
            }

            // Check content
            val content = contentIndex[page.id]
            val contentMatch = content?.contains(lowerQuery) == true
            if (contentMatch) {
                matchTypes.add(SearchMatchType.CONTENT)
                snippet = extractSnippet(content!!, lowerQuery)
            }

            if (matchTypes.isNotEmpty()) {
                // Determine best score for this page
                val score = when {
                    titleMatch != null -> titleMatch
                    tagMatch || summaryMatch -> SearchRelevance.PAGE_TAG_OR_SUMMARY
                    contentMatch -> SearchRelevance.PAGE_CONTENT_ONLY
                    else -> SearchRelevance.PAGE_CONTENT_ONLY
                }
                results.add(ScoredSearchResult.PageResult(page, matchTypes, snippet, score))
            }
        }

        // Sort by score (lower = better), then by name/title alphabetically
        return results.sortedWith(
            compareBy<ScoredSearchResult> { it.score }
                .thenBy {
                    when (it) {
                        is ScoredSearchResult.ItemResult -> it.item.name.lowercase()
                        is ScoredSearchResult.PageResult -> it.page.title.lowercase()
                    }
                }
        )
    }

    /**
     * Extract a snippet around the search match for preview.
     */
    private fun extractSnippet(content: String, query: String): String {
        val index = content.indexOf(query)
        if (index < 0) return ""

        val snippetRadius = 40
        val start = maxOf(0, index - snippetRadius)
        val end = minOf(content.length, index + query.length + snippetRadius)

        val prefix = if (start > 0) "..." else ""
        val suffix = if (end < content.length) "..." else ""

        return "$prefix${content.substring(start, end).trim()}$suffix"
    }

    fun getPage(pageId: String): HandbookPage? {
        pageCache[pageId]?.let { return it }

        val meta = getPages().find { it.id == pageId } ?: return null
        val content = loadPageContent(meta) ?: return null
        val rendered = markdownParser.parse(content)

        val page = HandbookPage(meta, content, rendered)
        pageCache[pageId] = page
        return page
    }

    private fun loadPageContent(meta: HandbookPageMeta): String? {
        // First try server override
        val serverHash = currentServerHash
        if (serverHash != null) {
            val serverFile = configDir.resolve(serverHash).resolve(meta.file)
            if (Files.exists(serverFile)) {
                return try {
                    Files.readString(serverFile)
                } catch (e: Exception) {
                    CivutilsMod.logger.error("Failed to load server page: ${meta.file}", e)
                    null
                }
            }
        }

        // Fall back to bundled content
        return try {
            val stream = javaClass.classLoader
                .getResourceAsStream("$HANDBOOK_RESOURCE_PATH/${meta.file}")
            stream?.bufferedReader()?.readText()
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load bundled page: ${meta.file}", e)
            null
        }
    }

    fun getDefaultPage(): HandbookPage? {
        val defaultId = getIndex().defaultPage
        return if (defaultId.isNotEmpty()) getPage(defaultId) else null
    }

    // === Navigation ===

    fun navigateTo(pageId: String) {
        if (historyIndex < history.size - 1) {
            history.subList(historyIndex + 1, history.size).clear()
        }

        history.add(pageId)
        historyIndex = history.size - 1
    }

    fun canGoBack(): Boolean = historyIndex > 0

    fun canGoForward(): Boolean = historyIndex < history.size - 1

    fun goBack(): String? {
        if (!canGoBack()) return null
        historyIndex--
        return history[historyIndex]
    }

    fun goForward(): String? {
        if (!canGoForward()) return null
        historyIndex++
        return history[historyIndex]
    }

    // === Utilities ===

    private fun getServerHash(): String {
        val mc = Minecraft.getInstance()
        val address = mc.currentServer?.ip ?: "singleplayer"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(address.lowercase().toByteArray())
        return hashBytes.take(8).joinToString("") { "%02x".format(it) }
    }

    fun hasContent(): Boolean = getPages().isNotEmpty() || hasItems()

    // === Items Database Access ===

    /**
     * Get an item by ID from the items database.
     */
    fun getItem(itemId: String): ItemDefinition? {
        itemCache[itemId]?.let { return it }
        val item = itemsIndex?.getItem(itemId)
        if (item != null) {
            itemCache[itemId] = item
        }
        return item
    }

    /**
     * Get all items in a category.
     */
    fun getItemsByCategory(category: ItemCategory): List<ItemDefinition> {
        return itemsIndex?.getItemsByCategory(category) ?: emptyList()
    }

    /**
     * Get all active item categories.
     */
    fun getItemCategories(): List<ItemCategory> {
        return itemsIndex?.getActiveCategories() ?: emptyList()
    }

    /**
     * Search items by name, summary, or tags.
     */
    fun searchItems(query: String): List<ItemDefinition> {
        return itemsIndex?.searchItems(query) ?: emptyList()
    }

    /**
     * Get all items that have the given tag.
     */
    fun getItemsByTag(tag: String): List<ItemDefinition> {
        return itemsIndex?.getItemsByTag(tag) ?: emptyList()
    }

    /**
     * Get all items that have any of the given tags (union, deduplicated, sorted by order).
     */
    fun getItemsByTags(tags: List<String>): List<ItemDefinition> {
        return itemsIndex?.getItemsByTags(tags) ?: emptyList()
    }

    /**
     * Get items unlocked at a specific class level (e.g., "blacksmith", 2).
     */
    fun getItemsByClassLevel(className: String, level: Int): List<ItemDefinition> {
        return itemsIndex?.getItemsByClassLevel(className, level) ?: emptyList()
    }

    /**
     * Get all items from the database.
     */
    fun getAllItems(): List<ItemDefinition> {
        return itemsIndex?.items ?: emptyList()
    }

    /**
     * Check if items database has content.
     */
    fun hasItems(): Boolean = (itemsIndex?.items?.size ?: 0) > 0

    /**
     * Find all items whose recipes use the given item ID as an ingredient.
     * Returns a list of pairs: (ItemDefinition, Recipe) for each matching recipe.
     */
    fun getRecipesUsingItem(itemId: String): List<Pair<ItemDefinition, Recipe>> {
        return itemsIndex?.getRecipesUsingItem(itemId) ?: emptyList()
    }

    /**
     * Get items that use the given item as an ingredient, grouped by relationship type.
     */
    fun getItemRelationships(itemId: String): List<ItemRelationGroup> {
        return itemsIndex?.getItemRelationships(itemId) ?: emptyList()
    }

    /**
     * Resolve an item ID to display item.
     * For custom items (no namespace), looks up in items database.
     */
    fun resolveItemDisplayId(itemId: String): String {
        // If already has namespace, return as-is
        if (itemId.contains(":")) return itemId

        // Look up in items database
        val item = getItem(itemId)
        return item?.renderItemId ?: "minecraft:barrier"
    }
}
