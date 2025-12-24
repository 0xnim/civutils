package xyz.nim.civutils.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.MinecraftClient
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.event.WorldJoinEvent
import xyz.nim.civutils.core.event.WorldLeaveEvent
import xyz.nim.civutils.core.model.Model
import xyz.nim.civutils.data.playertag.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Model for managing player tag data.
 * Handles per-server storage and provides APIs for tagging players.
 */
object PlayerTagModel : Model() {
    private val dataDir: Path = FabricLoader.getInstance().configDir
        .resolve(CivutilsMod.MOD_ID)
        .resolve("playerdata")

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    private var currentServerAddress: String? = null
    private var currentData: ServerData? = null
    private var dirty = false

    init {
        if (!Files.exists(dataDir)) {
            Files.createDirectories(dataDir)
        }
    }

    override fun reset() {
        // Note: reset() is called by the base Model class on both world join and leave.
        // We use a tick-based approach to load data after join.
        saveIfDirty()
        currentServerAddress = null
        currentData = null
        dirty = false
    }

    /**
     * Called each tick to check if we need to load data for a new server.
     */
    @Subscribe
    fun onTick(event: xyz.nim.civutils.core.event.ClientTickEvent) {
        val mc = MinecraftClient.getInstance()
        if (mc.world == null || mc.player == null) return

        val address = getServerAddress()
        if (currentServerAddress != address) {
            // Server changed, load new data
            saveIfDirty()
            currentServerAddress = address
            currentData = loadServerData(address)
            dirty = false
            CivutilsMod.logger.info("PlayerTagModel: Loaded data for server '$address'")
        }
    }

    /**
     * Get the current server address.
     */
    private fun getServerAddress(): String {
        val mc = MinecraftClient.getInstance()
        val serverInfo = mc.networkHandler?.serverInfo
        return serverInfo?.address ?: "singleplayer"
    }

    /**
     * Get the file path for a server's data.
     */
    private fun getServerDataFile(serverAddress: String): Path {
        val hash = hashServerAddress(serverAddress)
        return dataDir.resolve("$hash.json")
    }

    /**
     * Hash a server address to create a filename-safe identifier.
     */
    private fun hashServerAddress(address: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(address.lowercase().toByteArray())
        return hashBytes.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Load server data from disk.
     */
    private fun loadServerData(serverAddress: String): ServerData {
        val file = getServerDataFile(serverAddress)

        if (!Files.exists(file)) {
            CivutilsMod.logger.info("No player tag data found for '$serverAddress', creating new")
            return ServerData(serverAddress)
        }

        return try {
            val content = Files.readString(file)
            gson.fromJson(content, ServerData::class.java) ?: ServerData(serverAddress)
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load player tag data for '$serverAddress'", e)
            ServerData(serverAddress)
        }
    }

    /**
     * Save current server data to disk.
     */
    fun save() {
        val data = currentData ?: return
        val address = currentServerAddress ?: return
        val file = getServerDataFile(address)

        try {
            Files.writeString(file, gson.toJson(data))
            dirty = false
            CivutilsMod.logger.debug("Saved player tag data for '$address'")
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to save player tag data for '$address'", e)
        }
    }

    /**
     * Save if there are unsaved changes.
     */
    fun saveIfDirty() {
        if (dirty) {
            save()
        }
    }

    /**
     * Mark data as modified.
     */
    private fun markDirty() {
        dirty = true
    }

    // ============== Player APIs ==============

    /**
     * Get a tagged player by UUID.
     */
    fun getPlayer(uuid: String): TaggedPlayer? = currentData?.getPlayer(uuid)

    /**
     * Get a tagged player by name (case-insensitive).
     */
    fun getPlayerByName(name: String): TaggedPlayer? = currentData?.getPlayerByName(name)

    /**
     * Set an attribute for a player.
     */
    fun setPlayerAttribute(uuid: String, name: String, typeId: String, valueId: String): Boolean {
        val data = currentData ?: return false

        // Verify the attribute type and value exist
        val type = data.getAttributeType(typeId) ?: return false
        if (type.getValue(valueId) == null) return false

        val player = data.getOrCreatePlayer(uuid, name)
        player.setAttribute(typeId, valueId)
        player.lastKnownName = name
        markDirty()
        return true
    }

    /**
     * Remove an attribute from a player.
     */
    fun removePlayerAttribute(uuid: String, typeId: String): Boolean {
        val player = currentData?.getPlayer(uuid) ?: return false
        val result = player.removeAttribute(typeId)

        // Remove player entirely if they have no attributes and no notes
        if (!player.hasAttributes() && player.notes.isEmpty()) {
            currentData?.removePlayer(uuid)
        }

        if (result) markDirty()
        return result
    }

    /**
     * Remove all attributes from a player.
     */
    fun untagPlayer(uuid: String): Boolean {
        val result = currentData?.removePlayer(uuid) ?: false
        if (result) markDirty()
        return result
    }

    /**
     * Set a note for a player.
     */
    fun setPlayerNote(uuid: String, name: String, note: String): Boolean {
        val data = currentData ?: return false
        val player = data.getOrCreatePlayer(uuid, name)
        player.notes = note
        markDirty()
        return true
    }

    /**
     * Update a player's last seen location.
     */
    fun updatePlayerLastSeen(uuid: String, name: String, location: LocationSnapshot) {
        val data = currentData ?: return
        val player = data.getPlayer(uuid) ?: return
        player.updateLastSeen(location)
        player.lastKnownName = name
        markDirty()
    }

    /**
     * Get all tagged players.
     */
    fun getAllPlayers(): List<TaggedPlayer> = currentData?.players?.values?.toList() ?: emptyList()

    /**
     * Get all players with a specific attribute.
     */
    fun getPlayersWithAttribute(typeId: String, valueId: String? = null): List<TaggedPlayer> {
        return currentData?.getPlayersWithAttribute(typeId, valueId) ?: emptyList()
    }

    // ============== Attribute Type APIs ==============

    /**
     * Get all attribute types.
     */
    fun getAttributeTypes(): List<AttributeType> = currentData?.attributeTypes?.values?.toList() ?: emptyList()

    /**
     * Get an attribute type by ID.
     */
    fun getAttributeType(id: String): AttributeType? = currentData?.getAttributeType(id)

    /**
     * Create a new attribute type.
     */
    fun createAttributeType(id: String, displayName: String, renderPriority: Int = 0): Boolean {
        val data = currentData ?: return false
        if (data.attributeTypes.containsKey(id)) return false

        data.attributeTypes[id] = AttributeType(
            id = id,
            displayName = displayName,
            renderPriority = renderPriority
        )
        markDirty()
        return true
    }

    /**
     * Delete an attribute type and remove it from all players.
     */
    fun deleteAttributeType(id: String): Boolean {
        val data = currentData ?: return false
        if (!data.attributeTypes.containsKey(id)) return false

        // Remove from all players
        data.players.values.forEach { it.removeAttribute(id) }
        data.attributeTypes.remove(id)

        // Clean up players with no remaining attributes or notes
        data.players.entries.removeIf { (_, player) ->
            !player.hasAttributes() && player.notes.isEmpty()
        }

        markDirty()
        return true
    }

    /**
     * Add a value to an attribute type.
     */
    fun addAttributeValue(typeId: String, value: AttributeValue): Boolean {
        val type = currentData?.getAttributeType(typeId) ?: return false
        val result = type.addValue(value)
        if (result) markDirty()
        return result
    }

    /**
     * Remove a value from an attribute type.
     */
    fun removeAttributeValue(typeId: String, valueId: String): Boolean {
        val data = currentData ?: return false
        val type = data.getAttributeType(typeId) ?: return false

        // Remove this value from all players
        data.players.values.forEach { player ->
            if (player.getAttribute(typeId) == valueId) {
                player.removeAttribute(typeId)
            }
        }

        val result = type.removeValue(valueId)
        if (result) markDirty()
        return result
    }

    /**
     * Set the render priority for an attribute type.
     */
    fun setAttributePriority(typeId: String, priority: Int): Boolean {
        val data = currentData ?: return false
        val type = data.getAttributeType(typeId) ?: return false

        // Create updated type with new priority
        val updated = type.copy(renderPriority = priority)
        data.attributeTypes[typeId] = updated
        markDirty()
        return true
    }

    /**
     * Add default attribute types to the current server.
     */
    fun addDefaultAttributeTypes() {
        val data = currentData ?: return

        for (defaultType in DefaultAttributes.getAll()) {
            if (!data.attributeTypes.containsKey(defaultType.id)) {
                data.attributeTypes[defaultType.id] = defaultType
            }
        }
        markDirty()
    }

    /**
     * Check if we have data loaded for the current server.
     */
    fun hasData(): Boolean = currentData != null
}
