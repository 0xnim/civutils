package xyz.nim.civutils.models

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.client.Minecraft
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
        val mc = Minecraft.getInstance()
        if (mc.level == null || mc.player == null) return

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
        val mc = Minecraft.getInstance()
        val serverData = mc.currentServer
        return serverData?.ip ?: "singleplayer"
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
            val data = gson.fromJson(content, ServerData::class.java) ?: ServerData(serverAddress)
            migrateData(data)
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load player tag data for '$serverAddress'", e)
            ServerData(serverAddress)
        }
    }

    /**
     * Migrate old data format to new format and validate/repair data.
     * Old format: players keyed by UUID
     * New format: players keyed by lowercase name
     */
    private fun migrateData(data: ServerData): ServerData {
        // Check if we need to migrate (old data has UUIDs as keys)
        val needsMigration = data.players.keys.any { key ->
            // UUID pattern: 8-4-4-4-12 hex chars with dashes
            key.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
        }

        if (needsMigration) {
            CivutilsMod.logger.info("Migrating player tag data from UUID-keyed to name-keyed format...")

            // Create new map with name-based keys
            val migratedPlayers = mutableMapOf<String, TaggedPlayer>()

            for ((oldKey, player) in data.players) {
                // If the key looks like a UUID, store it as the player's uuid and use name as key
                if (oldKey.matches(Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))) {
                    if (player.uuid == null) {
                        player.uuid = oldKey
                    }
                }

                // Use name as the new key
                val newKey = player.name.lowercase()
                migratedPlayers[newKey] = player
            }

            // Replace players map
            data.players.clear()
            data.players.putAll(migratedPlayers)

            CivutilsMod.logger.info("Migration complete. Migrated ${migratedPlayers.size} players.")
        }

        // Validate and repair data
        validateAndRepairData(data)

        return data
    }

    /**
     * Validate player data and attempt to repair corrupted entries.
     * - Fix players with null/empty names by using the map key
     * - Remove entries that cannot be repaired
     *
     * Note: Gson can set non-nullable Kotlin fields to null by bypassing the constructor,
     * so we need to handle null defensively here.
     */
    @Suppress("SENSELESS_COMPARISON") // name CAN be null due to Gson reflection
    private fun validateAndRepairData(data: ServerData) {
        val toRemove = mutableListOf<String>()
        val toRename = mutableMapOf<String, String>() // oldKey -> newKey

        for ((key, player) in data.players) {
            // Check for null or empty player name (Gson can set non-nullable fields to null!)
            val nameIsInvalid = player.name == null || player.name.isBlank()

            if (nameIsInvalid) {
                // Try to use the map key as the name (if it looks like a valid player name)
                if (key.isNotEmpty() && !key.matches(Regex("[0-9a-fA-F-]{32,}"))) {
                    CivutilsMod.logger.warn("Repairing player with missing/empty name, using key: $key")
                    player.name = key
                } else {
                    CivutilsMod.logger.warn("Removing invalid player entry with no name and unusable key: $key")
                    toRemove.add(key)
                    continue
                }
            }

            // Check if the key matches the lowercase name (it should)
            val expectedKey = player.name.lowercase()
            if (key != expectedKey) {
                CivutilsMod.logger.warn("Fixing player key mismatch: '$key' -> '$expectedKey' for player '${player.name}'")
                toRename[key] = expectedKey
            }
        }

        // Remove invalid entries
        toRemove.forEach { data.players.remove(it) }

        // Fix key mismatches
        for ((oldKey, newKey) in toRename) {
            val player = data.players.remove(oldKey)
            if (player != null && !data.players.containsKey(newKey)) {
                data.players[newKey] = player
            }
        }

        if (toRemove.isNotEmpty() || toRename.isNotEmpty()) {
            CivutilsMod.logger.info("Data validation: removed ${toRemove.size} invalid entries, fixed ${toRename.size} key mismatches")
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
     * Get a tagged player by name (case-insensitive).
     * This is the primary lookup method.
     */
    fun getPlayer(name: String): TaggedPlayer? = currentData?.getPlayer(name)

    /**
     * Get a tagged player by UUID.
     * Searches all players to find one with matching UUID.
     */
    fun getPlayerByUuid(uuid: String): TaggedPlayer? = currentData?.getPlayerByUuid(uuid)

    /**
     * Alias for getPlayer for backwards compatibility.
     */
    fun getPlayerByName(name: String): TaggedPlayer? = getPlayer(name)

    /**
     * Set an attribute for a player by name.
     * UUID is optional and will be stored if provided.
     *
     * @param name The player's name (primary identifier)
     * @param typeId The attribute type ID
     * @param valueId The attribute value ID
     * @param uuid Optional UUID to associate with the player
     */
    fun setPlayerAttribute(name: String, typeId: String, valueId: String, uuid: String? = null): Boolean {
        val data = currentData ?: return false

        // Verify the attribute type and value exist
        val type = data.getAttributeType(typeId) ?: return false
        if (type.getValue(valueId) == null) return false

        val player = data.getOrCreatePlayer(name, uuid)
        player.setAttribute(typeId, valueId)
        markDirty()
        return true
    }

    /**
     * Legacy API - Set attribute with UUID as first param.
     * @deprecated Use setPlayerAttribute(name, typeId, valueId, uuid) instead
     */
    @Deprecated("Use name-based API", ReplaceWith("setPlayerAttribute(name, typeId, valueId, uuid)"))
    @JvmName("setPlayerAttributeLegacy")
    fun setPlayerAttributeWithUuid(uuid: String, name: String, typeId: String, valueId: String): Boolean {
        return setPlayerAttribute(name, typeId, valueId, uuid)
    }

    /**
     * Remove an attribute from a player by name.
     */
    fun removePlayerAttribute(name: String, typeId: String): Boolean {
        val player = currentData?.getPlayer(name) ?: return false
        val result = player.removeAttribute(typeId)

        // Remove player entirely if they have no attributes and no notes
        if (!player.hasAttributes() && player.notes.isEmpty()) {
            currentData?.removePlayer(name)
        }

        if (result) markDirty()
        return result
    }

    /**
     * Remove all attributes from a player by name.
     */
    fun untagPlayer(name: String): Boolean {
        val result = currentData?.removePlayer(name) ?: false
        if (result) markDirty()
        return result
    }

    /**
     * Set a note for a player.
     *
     * @param name The player's name
     * @param note The note text
     * @param uuid Optional UUID to associate
     */
    fun setPlayerNote(name: String, note: String, uuid: String? = null): Boolean {
        val data = currentData ?: return false
        val player = data.getOrCreatePlayer(name, uuid)
        player.notes = note
        markDirty()
        return true
    }

    /**
     * Update a player's last seen location.
     */
    fun updatePlayerLastSeen(name: String, location: LocationSnapshot, uuid: String? = null) {
        val data = currentData ?: return
        val player = data.getPlayer(name) ?: return
        player.updateLastSeen(location)
        if (uuid != null && player.uuid == null) {
            player.uuid = uuid
        }
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

    /**
     * Get nearby players from the world.
     * Returns online players within render distance.
     */
    fun getNearbyPlayers(): List<Pair<String, String?>> {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return emptyList()
        val world = mc.level ?: return emptyList()

        return world.players()
            .filter { it != player && it.distanceTo(player) < 64 }
            .sortedBy { it.distanceTo(player) }
            .map { it.gameProfile.name to it.stringUUID }
    }

    /**
     * Get online players from the player list.
     */
    fun getOnlinePlayers(): List<Pair<String, String>> {
        val mc = Minecraft.getInstance()
        return mc.connection?.listedOnlinePlayers?.map {
            it.profile.name to it.profile.id.toString()
        } ?: emptyList()
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
     * Update an existing attribute value.
     */
    fun updateAttributeValue(typeId: String, valueId: String, newValue: AttributeValue): Boolean {
        val type = currentData?.getAttributeType(typeId) ?: return false
        val result = type.updateValue(valueId, newValue)
        if (result) markDirty()
        return result
    }

    /**
     * Update an attribute type's properties.
     */
    fun updateAttributeType(typeId: String, displayName: String? = null, renderPriority: Int? = null): Boolean {
        val data = currentData ?: return false
        val type = data.getAttributeType(typeId) ?: return false

        val updated = type.copy(
            displayName = displayName ?: type.displayName,
            renderPriority = renderPriority ?: type.renderPriority
        )
        data.attributeTypes[typeId] = updated
        markDirty()
        return true
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
