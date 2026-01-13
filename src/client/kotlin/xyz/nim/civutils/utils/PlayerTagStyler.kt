package xyz.nim.civutils.utils

import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
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
    fun applyStyle(original: Component, playerName: String): Component {
        val feature = PlayerTagFeature.getInstance() ?: return original
        if (!feature.enabled) return original
        if (!feature.enableNameTags.value) return original

        val player = PlayerTagModel.getPlayerByName(playerName) ?: return original
        return applyStyleToText(original, player)
    }

    /**
     * Apply styling to text based on a tagged player's attributes.
     */
    fun applyStyleToText(original: Component, player: TaggedPlayer): Component {
        val styles = getPlayerStyles(player)
        if (styles.isEmpty()) return original

        // Get prefix from all attributes with icons
        val prefix = buildPrefix(player)

        // Get the highest priority style for color/formatting
        val primaryStyle = styles.maxByOrNull { it.first }?.second ?: return original

        // Create styled text
        val result = Component.empty()

        // Add prefix if present
        if (prefix.isNotEmpty()) {
            (result as MutableComponent).append(Component.literal(prefix).withStyle { applyAttributeStyle(it, primaryStyle) })
        }

        // Add the original name with styling
        (result as MutableComponent).append(Component.literal(original.string).withStyle { applyAttributeStyle(it, primaryStyle) })

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
        if (attrStyle.underline) result = result.withUnderlined(true)
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
        if (!feature.enabled) return ""
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

    /**
     * Get a styled component for a player name (for preview rendering).
     * Does not check if styling is enabled - always applies styling if player is tagged.
     */
    fun getStyledComponent(playerName: String): Component {
        val player = PlayerTagModel.getPlayerByName(playerName)
            ?: return Component.literal(playerName)

        val styles = getPlayerStyles(player)
        if (styles.isEmpty()) return Component.literal(playerName)

        val prefix = buildPrefix(player)
        val primaryStyle = styles.maxByOrNull { it.first }?.second
            ?: return Component.literal(playerName)

        val result = Component.empty() as MutableComponent

        if (prefix.isNotEmpty()) {
            result.append(Component.literal(prefix).withStyle { applyAttributeStyle(it, primaryStyle) })
        }

        result.append(Component.literal(playerName).withStyle { applyAttributeStyle(it, primaryStyle) })

        return result
    }
}
