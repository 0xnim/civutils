package xyz.nim.civutils.data.playertag

/**
 * Default attribute types that can be added to a server's data.
 * These are common presets that users can choose from.
 */
object DefaultAttributes {

    /**
     * Trust level attribute - for marking players as hostile, neutral, or friendly.
     */
    fun createTrustLevel(): AttributeType {
        return AttributeType(
            id = "trust",
            displayName = "Trust Level",
            renderPriority = 100,
            showInNameTag = true,
            showInTabList = true,
            showIconAboveHead = true,
            values = mutableListOf(
                AttributeValue(
                    id = "hostile",
                    displayName = "Hostile",
                    style = AttributeStyle(color = 0xFF5555, bold = true, prefix = "\u2694")
                ),
                AttributeValue(
                    id = "unknown",
                    displayName = "Unknown",
                    style = AttributeStyle(color = 0xAAAAAA, italic = true, prefix = "?")
                ),
                AttributeValue(
                    id = "neutral",
                    displayName = "Neutral",
                    style = AttributeStyle(color = 0xFFFF55, prefix = "\u2022")
                ),
                AttributeValue(
                    id = "trusted",
                    displayName = "Trusted",
                    style = AttributeStyle(color = 0x55FF55, prefix = "\u2714")
                ),
                AttributeValue(
                    id = "allied",
                    displayName = "Allied",
                    style = AttributeStyle(color = 0x55FFFF, bold = true, prefix = "\u2605")
                )
            )
        )
    }

    /**
     * CivMC class attribute - for marking player classes/professions.
     */
    fun createCivClass(): AttributeType {
        return AttributeType(
            id = "class",
            displayName = "Class",
            renderPriority = 50,
            showInNameTag = true,
            showInTabList = false,
            showIconAboveHead = true,
            values = mutableListOf(
                AttributeValue(
                    id = "guardsman",
                    displayName = "Guardsman",
                    style = AttributeStyle(color = 0x5555FF, prefix = "\u2694") // Sword
                ),
                AttributeValue(
                    id = "farmer",
                    displayName = "Farmer",
                    style = AttributeStyle(color = 0x55FF55, prefix = "\uD83C\uDF3E") // Wheat
                ),
                AttributeValue(
                    id = "builder",
                    displayName = "Builder",
                    style = AttributeStyle(color = 0xFFAA00, prefix = "\uD83D\uDD28") // Hammer
                ),
                AttributeValue(
                    id = "miner",
                    displayName = "Miner",
                    style = AttributeStyle(color = 0x888888, prefix = "\u26CF") // Pick
                ),
                AttributeValue(
                    id = "librarian",
                    displayName = "Librarian",
                    style = AttributeStyle(color = 0xAA55FF, prefix = "\uD83D\uDCD6") // Book
                ),
                AttributeValue(
                    id = "healer",
                    displayName = "Healer",
                    style = AttributeStyle(color = 0xFF5555, prefix = "\u2764") // Heart
                ),
                AttributeValue(
                    id = "blacksmith",
                    displayName = "Blacksmith",
                    style = AttributeStyle(color = 0xFF5500, prefix = "\uD83D\uDD25") // Fire
                )
            )
        )
    }

    /**
     * Citizen rank attribute - for marking civ permission levels.
     */
    fun createCivRank(): AttributeType {
        return AttributeType(
            id = "rank",
            displayName = "Civ Rank",
            renderPriority = 25,
            showInNameTag = true,
            showInTabList = true,
            showIconAboveHead = false,
            values = mutableListOf(
                AttributeValue(
                    id = "outsider",
                    displayName = "Outsider",
                    style = AttributeStyle(color = 0xAAAAAA, italic = true)
                ),
                AttributeValue(
                    id = "citizen",
                    displayName = "Citizen",
                    style = AttributeStyle(color = 0xFFFF55)
                ),
                AttributeValue(
                    id = "trusted",
                    displayName = "Trusted Citizen",
                    style = AttributeStyle(color = 0x55FF55)
                ),
                AttributeValue(
                    id = "officer",
                    displayName = "Officer",
                    style = AttributeStyle(color = 0x55FFFF, bold = true)
                ),
                AttributeValue(
                    id = "leader",
                    displayName = "Leader",
                    style = AttributeStyle(color = 0xFFAA00, bold = true, prefix = "\u2654")
                )
            )
        )
    }

    /**
     * Get all default attribute types.
     */
    fun getAll(): List<AttributeType> = listOf(
        createTrustLevel(),
        createCivClass(),
        createCivRank()
    )
}
