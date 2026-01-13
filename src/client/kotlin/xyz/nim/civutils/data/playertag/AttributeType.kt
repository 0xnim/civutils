package xyz.nim.civutils.data.playertag

/**
 * An attribute type definition.
 * Users can create multiple attribute types like "Trust Level", "Class", "Civ Rank".
 * Each type has a set of possible values with their own styling.
 */
data class AttributeType(
    val id: String,
    val displayName: String,
    val values: MutableList<AttributeValue> = mutableListOf(),
    val renderPriority: Int = 0,
    val showInNameTag: Boolean = true,
    val showInTabList: Boolean = true,
    val showIconAboveHead: Boolean = true
) {
    /**
     * Get a value by its ID.
     */
    fun getValue(valueId: String): AttributeValue? = values.find { it.id == valueId }

    /**
     * Add a new value to this attribute type.
     */
    fun addValue(value: AttributeValue): Boolean {
        if (values.any { it.id == value.id }) return false
        values.add(value)
        return true
    }

    /**
     * Remove a value by its ID.
     */
    fun removeValue(valueId: String): Boolean {
        return values.removeIf { it.id == valueId }
    }

    /**
     * Update an existing value.
     * Replaces the value with the same ID with the new value.
     */
    fun updateValue(valueId: String, newValue: AttributeValue): Boolean {
        val index = values.indexOfFirst { it.id == valueId }
        if (index < 0) return false
        values[index] = newValue
        return true
    }
}
