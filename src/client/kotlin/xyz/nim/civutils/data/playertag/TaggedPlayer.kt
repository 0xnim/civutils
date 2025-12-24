package xyz.nim.civutils.data.playertag

/**
 * A tagged player with multiple attributes assigned.
 * Stores the player's UUID, name, assigned attributes, notes, and tracking data.
 */
data class TaggedPlayer(
    val uuid: String,
    var lastKnownName: String,
    val attributes: MutableMap<String, String> = mutableMapOf(),
    var notes: String = "",
    var lastSeen: LocationSnapshot? = null,
    var markedTimestamp: Long = System.currentTimeMillis(),
    var lastSeenTimestamp: Long = System.currentTimeMillis()
) {
    /**
     * Set an attribute for this player.
     * @param typeId The attribute type ID (e.g., "trust")
     * @param valueId The value ID (e.g., "hostile")
     */
    fun setAttribute(typeId: String, valueId: String) {
        attributes[typeId] = valueId
    }

    /**
     * Remove an attribute from this player.
     * @param typeId The attribute type ID to remove
     * @return true if the attribute was removed
     */
    fun removeAttribute(typeId: String): Boolean {
        return attributes.remove(typeId) != null
    }

    /**
     * Get the value ID for an attribute type.
     * @param typeId The attribute type ID
     * @return The value ID, or null if not set
     */
    fun getAttribute(typeId: String): String? = attributes[typeId]

    /**
     * Check if this player has any attributes assigned.
     */
    fun hasAttributes(): Boolean = attributes.isNotEmpty()

    /**
     * Update the last seen location.
     */
    fun updateLastSeen(location: LocationSnapshot) {
        lastSeen = location
        lastSeenTimestamp = System.currentTimeMillis()
    }
}
