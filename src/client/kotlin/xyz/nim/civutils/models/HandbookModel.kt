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
            val indexStream = javaClass.classLoader
                .getResourceAsStream("$HANDBOOK_RESOURCE_PATH/$INDEX_FILE")

            if (indexStream != null) {
                bundledIndex = gson.fromJson(
                    InputStreamReader(indexStream),
                    HandbookIndex::class.java
                )
                CivutilsMod.logger.info("Loaded bundled handbook: ${bundledIndex?.pages?.size ?: 0} pages")
            } else {
                CivutilsMod.logger.info("No bundled handbook found")
                bundledIndex = HandbookIndex()
            }
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load bundled handbook", e)
            bundledIndex = HandbookIndex()
        }

        // Load custom item definitions (legacy)
        loadCustomItems()

        // Load structured items database
        loadItemsDatabase()
    }

    private fun loadItemsDatabase() {
        try {
            val itemsStream = javaClass.classLoader
                .getResourceAsStream("$HANDBOOK_RESOURCE_PATH/items.json")

            if (itemsStream != null) {
                itemsIndex = gson.fromJson(
                    InputStreamReader(itemsStream),
                    ItemsIndex::class.java
                )
                CivutilsMod.logger.info("Loaded ${itemsIndex?.items?.size ?: 0} items from database")

                // Load item definitions into ItemMatcher for NBT matching
                itemsIndex?.getCustomItems()?.let { customItems ->
                    val definitions = customItems.mapNotNull { item ->
                        item.filters?.let { filters ->
                            CustomItemDefinition(item.id, item.id, filters)
                        }
                    }
                    if (definitions.isNotEmpty()) {
                        ItemMatcher.loadDefinitions(definitions)
                        CivutilsMod.logger.info("Loaded ${definitions.size} item filters from items.json")
                    }
                }
            } else {
                CivutilsMod.logger.debug("No items.json found")
                itemsIndex = ItemsIndex()
            }
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load items database", e)
            itemsIndex = ItemsIndex()
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
