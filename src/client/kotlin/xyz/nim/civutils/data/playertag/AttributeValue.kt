package xyz.nim.civutils.data.playertag

/**
 * A possible value for an attribute type.
 * For example, within a "Trust Level" attribute type,
 * values might be "Hostile", "Neutral", "Trusted".
 */
data class AttributeValue(
    val id: String,
    val displayName: String,
    val style: AttributeStyle = AttributeStyle.DEFAULT
)
