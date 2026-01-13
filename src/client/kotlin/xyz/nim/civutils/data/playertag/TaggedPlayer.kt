package xyz.nim.civutils.data.playertag

import com.google.gson.annotations.SerializedName

/**
 * A tagged player with multiple attributes assigned.
 * Uses name as the primary identifier. UUID is stored when known but is optional.
 * Stores the player's name, assigned attributes, notes, and tracking data.
 */
data class TaggedPlayer(
    /** Primary identifier - the player's name (case-preserved but lookups are case-insensitive) */
    // Accepts both "name" (old format) and "lastKnownName" (new format) for backwards compatibility
    @SerializedName(value = "name", alternate = ["lastKnownName"])
    var name: String,
    /** Optional UUID - stored when known, used for skin lookups */
    var uuid: String? = null,
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
