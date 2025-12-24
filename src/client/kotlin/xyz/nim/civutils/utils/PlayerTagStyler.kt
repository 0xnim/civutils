package xyz.nim.civutils.utils

import net.minecraft.text.MutableText
import net.minecraft.text.Style
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import xyz.nim.civutils.data.playertag.AttributeStyle
import xyz.nim.civutils.data.playertag.TaggedPlayer
import xyz.nim.civutils.features.players.PlayerTagFeature
import xyz.nim.civutils.models.PlayerTagModel

/**
 * Utility for applying attribute-based styling to player names.
 */
object PlayerTagStyler {

    /**
     * Apply styling to a player's name text based on their tagged attributes.
     * Returns the original text if the player isn't tagged or styling is disabled.
     */
    fun applyStyle(original: Text, playerName: String): Text {
        val feature = PlayerTagFeature.getInstance() ?: return original
        if (!feature.enableNameTags.value) return original

        val player = PlayerTagModel.getPlayerByName(playerName) ?: return original
        return applyStyleToText(original, player)
    }

    /**
     * Apply styling to text based on a tagged player's attributes.
     */
    fun applyStyleToText(original: Text, player: TaggedPlayer): Text {
        val styles = getPlayerStyles(player)
        if (styles.isEmpty()) return original

        // Get prefix from all attributes with icons
        val prefix = buildPrefix(player)

        // Get the highest priority style for color/formatting
        val primaryStyle = styles.maxByOrNull { it.first }?.second ?: return original

        // Create styled text
        val result = Text.empty()

        // Add prefix if present
        if (prefix.isNotEmpty()) {
            result.append(Text.literal(prefix).styled { applyAttributeStyle(it, primaryStyle) })
        }

        // Add the original name with styling
        result.append(Text.literal(original.string).styled { applyAttributeStyle(it, primaryStyle) })

        return result
    }

    /**
     * Get all applicable styles for a player, sorted by priority.
     */
    private fun getPlayerStyles(player: TaggedPlayer): List<Pair<Int, AttributeStyle>> {
        val styles = mutableListOf<Pair<Int, AttributeStyle>>()

        for ((typeId, valueId) in player.attributes) {
            val type = PlayerTagModel.getAttributeType(typeId) ?: continue
            val value = type.getValue(valueId) ?: continue
            styles.add(type.renderPriority to value.style)
        }

        return styles.sortedByDescending { it.first }
    }

    /**
     * Build a prefix string from all attribute icons.
     */
    private fun buildPrefix(player: TaggedPlayer): String {
        val prefixes = mutableListOf<String>()

        // Sort by priority (highest first)
        val sortedAttributes = player.attributes.entries
            .mapNotNull { (typeId, valueId) ->
                val type = PlayerTagModel.getAttributeType(typeId) ?: return@mapNotNull null
                val value = type.getValue(valueId) ?: return@mapNotNull null
                Triple(type.renderPriority, type.showIconAboveHead, value.style.prefix)
            }
            .filter { it.second && it.third.isNotEmpty() }
            .sortedByDescending { it.first }

        for ((_, _, prefix) in sortedAttributes) {
            prefixes.add(prefix)
        }

        return if (prefixes.isNotEmpty()) {
            prefixes.joinToString("") + " "
        } else {
            ""
        }
    }

    /**
     * Apply an AttributeStyle to a Minecraft Style.
     */
    private fun applyAttributeStyle(style: Style, attrStyle: AttributeStyle): Style {
        var result = style.withColor(attrStyle.color)

        if (attrStyle.bold) result = result.withBold(true)
        if (attrStyle.italic) result = result.withItalic(true)
        if (attrStyle.underline) result = result.withUnderline(true)
        if (attrStyle.strikethrough) result = result.withStrikethrough(true)

        return result
    }

    /**
     * Check if a player by name is tagged.
     */
    fun isTagged(playerName: String): Boolean {
        return PlayerTagModel.getPlayerByName(playerName) != null
    }

    /**
     * Get the prefix for a player (for rendering above head).
     */
    fun getPrefix(playerName: String): String {
        val feature = PlayerTagFeature.getInstance() ?: return ""
        if (!feature.enableIconsAboveHead.value) return ""

        val player = PlayerTagModel.getPlayerByName(playerName) ?: return ""
        return buildPrefix(player).trim()
    }

    /**
     * Get the primary color for a player.
     */
    fun getPrimaryColor(playerName: String): Int? {
        val player = PlayerTagModel.getPlayerByName(playerName) ?: return null
        val styles = getPlayerStyles(player)
        return styles.maxByOrNull { it.first }?.second?.color
    }
}
