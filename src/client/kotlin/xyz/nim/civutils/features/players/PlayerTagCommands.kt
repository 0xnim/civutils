package xyz.nim.civutils.features.players

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.gui.screens.PlayerTagScreen
import xyz.nim.civutils.gui.screens.QuickTagScreen
import xyz.nim.civutils.models.PlayerTagModel

/**
 * Client-side commands for the player tagging system.
 *
 * Commands:
 *   /tag                     - Open the tag management GUI
 *   /tag <player>            - Open quick tag popup for player
 *   /tag <player> <value>    - Instant tag (e.g., /tag Steve hostile)
 *   /tag <player> clear      - Remove all tags from player
 *
 * Value shortcuts (maps to trust type):
 *   hostile, unknown, neutral, trusted, allied
 *
 * Explicit type:value format also supported:
 *   /tag Steve class:archer
 *   /tag Steve rank:leader
 */
object PlayerTagCommands {

    private val mc: Minecraft get() = Minecraft.getInstance()

    /**
     * Shortcuts that map simple values to trust type.
     * These are the most common tags used in PvP scenarios.
     */
    private val VALUE_SHORTCUTS = mapOf(
        "hostile" to ("trust" to "hostile"),
        "unknown" to ("trust" to "unknown"),
        "neutral" to ("trust" to "neutral"),
        "trusted" to ("trust" to "trusted"),
        "allied" to ("trust" to "allied"),
        // Also support short forms
        "h" to ("trust" to "hostile"),
        "u" to ("trust" to "unknown"),
        "n" to ("trust" to "neutral"),
        "t" to ("trust" to "trusted"),
        "a" to ("trust" to "allied"),
        // Enemy/friend aliases
        "enemy" to ("trust" to "hostile"),
        "friend" to ("trust" to "trusted"),
        "ally" to ("trust" to "allied")
    )

    /**
     * Register all commands.
     */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            registerCommands(dispatcher)
        }
    }

    private fun registerCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        // Register both /ctag (client-side, always works) and /tag (may conflict with server)
        val commandBuilder = { literal: String ->
            ClientCommandManager.literal(literal)
                // /tag - open the GUI
                .executes { ctx -> openGui(ctx) }
                // /tag <player> [value]
                .then(
                    ClientCommandManager.argument("player", StringArgumentType.word())
                        .suggests(PLAYER_SUGGESTIONS)
                        // /tag <player> - open quick tag popup
                        .executes { ctx -> openQuickTag(ctx) }
                        // /tag <player> <value>
                        .then(
                            ClientCommandManager.argument("value", StringArgumentType.word())
                                .suggests(VALUE_SUGGESTIONS)
                                .executes { ctx -> tagPlayer(ctx) }
                        )
                )
        }

        // Primary command - /ctag (client tag, no server conflicts)
        dispatcher.register(commandBuilder("ctag"))
        // Alias - /tag (may conflict with server commands)
        dispatcher.register(commandBuilder("tag"))
    }

    // ============== Suggestion Providers ==============

    private val PLAYER_SUGGESTIONS = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        val input = builder.remaining.lowercase()
        val suggestions = mutableSetOf<String>()

        // 1. Nearby players first (highest priority for quick tagging)
        PlayerTagModel.getNearbyPlayers().forEach { (name, _) ->
            suggestions.add(name)
        }

        // 2. Online players from tab list
        mc.connection?.listedOnlinePlayers?.forEach { entry ->
            suggestions.add(entry.profile.name)
        }

        // 3. Already tagged players (for editing/clearing)
        PlayerTagModel.getAllPlayers().forEach { player ->
            suggestions.add(player.name)
        }

        // Filter and add suggestions
        suggestions
            .filter { input.isEmpty() || it.lowercase().contains(input) }
            .sortedBy {
                // Prioritize exact prefix matches
                if (it.lowercase().startsWith(input)) 0 else 1
            }
            .forEach { builder.suggest(it) }

        builder.buildFuture()
    }

    private val VALUE_SUGGESTIONS = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        // Suggest shortcuts for trust (most common)
        builder.suggest("hostile")
        builder.suggest("neutral")
        builder.suggest("trusted")
        builder.suggest("allied")
        builder.suggest("unknown")
        builder.suggest("clear")

        // Also suggest type:value format for all attribute types
        PlayerTagModel.getAttributeTypes().forEach { type ->
            type.values.forEach { value ->
                builder.suggest("${type.id}:${value.id}")
                // Also suggest just the value ID if it's unique
                builder.suggest(value.id)
            }
        }
        builder.buildFuture()
    }

    // ============== Command Handlers ==============

    /**
     * Check if the feature is available and user-enabled. If not, show an error message.
     * Note: We check userEnabled.value (user preference) rather than enabled (runtime state)
     * since the commands work with PlayerTagModel which is always active.
     */
    private fun checkFeatureEnabled(ctx: CommandContext<FabricClientCommandSource>): Boolean {
        val feature = PlayerTagFeature.getInstance()
        if (feature == null) {
            sendError(ctx, "Player Tags feature is not registered.")
            return false
        }
        // Check user preference - the model works regardless of runtime enabled state
        if (!feature.userEnabled.value) {
            sendError(ctx, "Player Tags feature is disabled. Enable it in the config menu.")
            return false
        }
        return true
    }

    private fun openGui(ctx: CommandContext<FabricClientCommandSource>): Int {
        if (!checkFeatureEnabled(ctx)) return 0
        // Schedule on next tick to avoid issues with command execution context
        pendingScreen = PlayerTagScreen()
        return 1
    }

    /** Screen to open on next tick */
    var pendingScreen: Screen? = null

    private fun openQuickTag(ctx: CommandContext<FabricClientCommandSource>): Int {
        if (!checkFeatureEnabled(ctx)) return 0
        val playerName = StringArgumentType.getString(ctx, "player")

        // Try to get UUID from online player list (optional, for skin loading)
        val uuid = getPlayerUuid(playerName)

        pendingScreen = QuickTagScreen(playerName, uuid)
        return 1
    }

    private fun tagPlayer(ctx: CommandContext<FabricClientCommandSource>): Int {
        if (!checkFeatureEnabled(ctx)) return 0
        val playerName = StringArgumentType.getString(ctx, "player")
        val valueArg = StringArgumentType.getString(ctx, "value").lowercase()

        // Handle "clear" command
        if (valueArg == "clear") {
            return clearPlayer(ctx, playerName)
        }

        // Try to get UUID (optional, just for skin caching)
        val uuid = getPlayerUuid(playerName)

        // Parse the value argument
        val (typeId, valueId) = parseValueArgument(valueArg)
        if (typeId == null || valueId == null) {
            sendError(ctx, "Unknown value '$valueArg'. Use: hostile, neutral, trusted, allied, unknown, or type:value")
            return 0
        }

        // Verify the attribute type and value exist
        val type = PlayerTagModel.getAttributeType(typeId)
        if (type == null) {
            // Auto-add default types if trust is requested
            if (typeId == "trust") {
                PlayerTagModel.addDefaultAttributeTypes()
            } else {
                sendError(ctx, "Attribute type '$typeId' not found. Use /tag to create it first.")
                return 0
            }
        }

        val verifiedType = PlayerTagModel.getAttributeType(typeId) ?: return 0
        val value = verifiedType.getValue(valueId)
        if (value == null) {
            sendError(ctx, "Value '$valueId' not found in type '$typeId'.")
            return 0
        }

        // Apply the tag using name as primary key
        if (PlayerTagModel.setPlayerAttribute(playerName, typeId, valueId, uuid)) {
            sendSuccess(ctx, "Tagged $playerName as ${value.displayName}")
            return 1
        } else {
            sendError(ctx, "Failed to tag player.")
            return 0
        }
    }

    private fun clearPlayer(ctx: CommandContext<FabricClientCommandSource>, playerName: String): Int {
        val player = PlayerTagModel.getPlayer(playerName)
        if (player == null) {
            sendError(ctx, "Player '$playerName' is not tagged.")
            return 0
        }

        if (PlayerTagModel.untagPlayer(playerName)) {
            sendSuccess(ctx, "Cleared all tags from $playerName")
            return 1
        } else {
            sendError(ctx, "Failed to clear tags.")
            return 0
        }
    }

    /**
     * Parse value argument into (typeId, valueId).
     * Supports:
     *   - Shortcuts: "hostile" -> ("trust", "hostile")
     *   - Explicit: "class:archer" -> ("class", "archer")
     */
    private fun parseValueArgument(value: String): Pair<String?, String?> {
        // Check shortcuts first
        VALUE_SHORTCUTS[value]?.let { return it }

        // Check for type:value format
        if (value.contains(":")) {
            val parts = value.split(":", limit = 2)
            if (parts.size == 2) {
                return parts[0] to parts[1]
            }
        }

        // Try to find this value in any attribute type
        for (type in PlayerTagModel.getAttributeTypes()) {
            if (type.getValue(value) != null) {
                return type.id to value
            }
        }

        return null to null
    }

    // ============== Utility Functions ==============

    private fun getPlayerUuid(name: String): String? {
        // First check online players
        val onlineEntry = mc.connection?.listedOnlinePlayers?.find {
            it.profile.name.equals(name, ignoreCase = true)
        }
        if (onlineEntry != null) {
            return onlineEntry.profile.id.toString()
        }

        // Fall back to already tagged players
        val taggedPlayer = PlayerTagModel.getPlayerByName(name)
        return taggedPlayer?.uuid
    }

    private fun sendSuccess(ctx: CommandContext<FabricClientCommandSource>, message: String) {
        ctx.source.sendFeedback(Component.literal("§a[Tag] §f$message"))
    }

    private fun sendError(ctx: CommandContext<FabricClientCommandSource>, message: String) {
        ctx.source.sendError(Component.literal("§c[Tag] §f$message"))
    }
}
