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

    private val pageCache = mutableMapOf<String, HandbookPage>()
    private var currentServerHash: String? = null

    // Navigation history
    private val history = mutableListOf<String>()
    private var historyIndex = -1

    override fun reset() {
        serverIndex = null
        mergedIndex = null
        pageCache.clear()
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
    }

    // === Page Access ===

    fun getIndex(): HandbookIndex = mergedIndex ?: bundledIndex ?: HandbookIndex()

    fun getCategories(): List<HandbookCategory> = getIndex().categories

    fun getPages(): List<HandbookPageMeta> = getIndex().pages

    fun getPagesInCategory(categoryId: String): List<HandbookPageMeta> {
        return getPages().filter { it.category == categoryId }
    }

    fun searchPages(query: String): List<HandbookPageMeta> {
        if (query.isBlank()) return getPages()

        val lowerQuery = query.lowercase()
        return getPages().filter { page ->
            page.title.lowercase().contains(lowerQuery) ||
                    page.tags.any { it.lowercase().contains(lowerQuery) } ||
                    page.summary.lowercase().contains(lowerQuery)
        }
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

    fun hasContent(): Boolean = getPages().isNotEmpty()
}
