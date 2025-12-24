package xyz.nim.civutils.data.playertag

/**
 * Style configuration for an attribute value.
 * Defines how a player's name should be rendered when they have this attribute.
 */
data class AttributeStyle(
    val color: Int = 0xFFFFFF,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val prefix: String = ""
) {
    companion object {
        val DEFAULT = AttributeStyle()

        // Common preset styles
        val RED = AttributeStyle(color = 0xFF5555, bold = true)
        val GREEN = AttributeStyle(color = 0x55FF55)
        val YELLOW = AttributeStyle(color = 0xFFFF55)
        val CYAN = AttributeStyle(color = 0x55FFFF)
        val GRAY = AttributeStyle(color = 0xAAAAAA, italic = true)
    }
}
