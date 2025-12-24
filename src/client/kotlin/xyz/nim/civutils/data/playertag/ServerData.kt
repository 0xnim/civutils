package xyz.nim.civutils.data.playertag

/**
 * Container for all player tag data for a specific server.
 * Each server has its own separate data file.
 */
data class ServerData(
    val serverAddress: String,
    val attributeTypes: MutableMap<String, AttributeType> = mutableMapOf(),
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
     * Get a tagged player by UUID.
     */
    fun getPlayer(uuid: String): TaggedPlayer? = players[uuid]

    /**
     * Get a tagged player by name (case-insensitive).
     */
    fun getPlayerByName(name: String): TaggedPlayer? {
        return players.values.find { it.lastKnownName.equals(name, ignoreCase = true) }
    }

    /**
     * Get or create a tagged player.
     */
    fun getOrCreatePlayer(uuid: String, name: String): TaggedPlayer {
        return players.getOrPut(uuid) { TaggedPlayer(uuid, name) }
    }

    /**
     * Remove a player's tag data.
     */
    fun removePlayer(uuid: String): Boolean {
        return players.remove(uuid) != null
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
