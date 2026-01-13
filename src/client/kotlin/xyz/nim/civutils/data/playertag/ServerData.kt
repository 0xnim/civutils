package xyz.nim.civutils.data.playertag

/**
 * Container for all player tag data for a specific server.
 * Each server has its own separate data file.
 *
 * Players are indexed by name (case-insensitive) as the primary key.
 * UUID is stored when known but is not required for tagging.
 */
data class ServerData(
    val serverAddress: String,
    val attributeTypes: MutableMap<String, AttributeType> = mutableMapOf(),
    /** Players indexed by lowercase name for case-insensitive lookup */
    val players: MutableMap<String, TaggedPlayer> = mutableMapOf()
) {
    /**
     * Get or create an attribute type.
     */
    fun getOrCreateAttributeType(id: String, displayName: String): AttributeType {
        return attributeTypes.getOrPut(id) { AttributeType(id, displayName) }
    }

    /**
     * Get an attribute type by ID.
     */
    fun getAttributeType(id: String): AttributeType? = attributeTypes[id]

    /**
     * Get a tagged player by name (case-insensitive).
     * This is the primary lookup method.
     */
    fun getPlayer(name: String): TaggedPlayer? = players[name.lowercase()]

    /**
     * Get a tagged player by UUID.
     * Searches through all players to find one with matching UUID.
     */
    fun getPlayerByUuid(uuid: String): TaggedPlayer? {
        return players.values.find { it.uuid == uuid }
    }

    /**
     * Get or create a tagged player by name.
     *
     * @param name The player's name (case-preserved in storage, case-insensitive for lookup)
     * @param uuid Optional UUID to associate with the player
     */
    fun getOrCreatePlayer(name: String, uuid: String? = null): TaggedPlayer {
        val key = name.lowercase()
        val existing = players[key]
        if (existing != null) {
            // Update UUID if provided and player doesn't have one
            if (uuid != null && existing.uuid == null) {
                existing.uuid = uuid
            }
            // Update name case if different
            if (existing.name != name) {
                existing.name = name
            }
            return existing
        }

        val player = TaggedPlayer(name = name, uuid = uuid)
        players[key] = player
        return player
    }

    /**
     * Remove a player's tag data by name.
     */
    fun removePlayer(name: String): Boolean {
        return players.remove(name.lowercase()) != null
    }

    /**
     * Remove a player's tag data by UUID.
     */
    fun removePlayerByUuid(uuid: String): Boolean {
        val player = getPlayerByUuid(uuid) ?: return false
        return removePlayer(player.name)
    }

    /**
     * Get all players with a specific attribute value.
     */
    fun getPlayersWithAttribute(typeId: String, valueId: String? = null): List<TaggedPlayer> {
        return players.values.filter { player ->
            val playerValue = player.getAttribute(typeId)
            if (valueId != null) {
                playerValue == valueId
            } else {
                playerValue != null
            }
        }
    }
}
