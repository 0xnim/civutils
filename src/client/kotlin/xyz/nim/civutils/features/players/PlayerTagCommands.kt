package xyz.nim.civutils.features.players

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.MinecraftClient
import net.minecraft.text.Text
import xyz.nim.civutils.gui.screens.PlayerTagScreen
import xyz.nim.civutils.models.PlayerTagModel

/**
 * Client-side commands for the player tagging system.
 * Most functionality is in the GUI - commands are minimal.
 */
object PlayerTagCommands {

    private val mc: MinecraftClient get() = MinecraftClient.getInstance()

    /**
     * Register all commands.
     */
    fun register() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            registerCommands(dispatcher)
        }
    }

    private fun registerCommands(dispatcher: CommandDispatcher<FabricClientCommandSource>) {
        dispatcher.register(
            ClientCommandManager.literal("tag")
                // /tag - open the GUI
                .executes { ctx -> openGui(ctx) }
                // /tag mark <player> <type> <value> - quick mark
                .then(
                    ClientCommandManager.literal("mark")
                        .then(
                            ClientCommandManager.argument("player", StringArgumentType.word())
                                .suggests(PLAYER_SUGGESTIONS)
                                .then(
                                    ClientCommandManager.argument("type", StringArgumentType.word())
                                        .suggests(ATTRIBUTE_TYPE_SUGGESTIONS)
                                        .then(
                                            ClientCommandManager.argument("value", StringArgumentType.word())
                                                .suggests(ATTRIBUTE_VALUE_SUGGESTIONS)
                                                .executes { ctx -> markPlayer(ctx) }
                                        )
                                )
                        )
                )
                // /tag unmark <player> - quick unmark
                .then(
                    ClientCommandManager.literal("unmark")
                        .then(
                            ClientCommandManager.argument("player", StringArgumentType.word())
                                .suggests(TAGGED_PLAYER_SUGGESTIONS)
                                .executes { ctx -> unmarkPlayer(ctx) }
                        )
                )
        )
    }

    // ============== Suggestion Providers ==============

    private val PLAYER_SUGGESTIONS = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        mc.networkHandler?.playerList?.forEach { entry ->
            builder.suggest(entry.profile.name)
        }
        builder.buildFuture()
    }

    private val TAGGED_PLAYER_SUGGESTIONS = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        PlayerTagModel.getAllPlayers().forEach { player ->
            builder.suggest(player.lastKnownName)
        }
        builder.buildFuture()
    }

    private val ATTRIBUTE_TYPE_SUGGESTIONS = SuggestionProvider<FabricClientCommandSource> { _, builder ->
        PlayerTagModel.getAttributeTypes().forEach { type ->
            builder.suggest(type.id)
        }
        builder.buildFuture()
    }

    private val ATTRIBUTE_VALUE_SUGGESTIONS = SuggestionProvider<FabricClientCommandSource> { ctx, builder ->
        val typeId = try {
            StringArgumentType.getString(ctx, "type")
        } catch (e: Exception) {
            null
        }

        if (typeId != null) {
            PlayerTagModel.getAttributeType(typeId)?.values?.forEach { value ->
                builder.suggest(value.id)
            }
        }
        builder.buildFuture()
    }

    // ============== Command Handlers ==============

    private fun openGui(ctx: CommandContext<FabricClientCommandSource>): Int {
        // Schedule GUI opening on the main thread
        mc.send {
            mc.setScreen(PlayerTagScreen())
        }
        return 1
    }

    private fun markPlayer(ctx: CommandContext<FabricClientCommandSource>): Int {
        val playerName = StringArgumentType.getString(ctx, "player")
        val typeId = StringArgumentType.getString(ctx, "type")
        val valueId = StringArgumentType.getString(ctx, "value")

        // Try to get UUID from online player list
        val uuid = getPlayerUuid(playerName)
        if (uuid == null) {
            sendError(ctx, "Player '$playerName' not found. They must be online.")
            return 0
        }

        val type = PlayerTagModel.getAttributeType(typeId)
        if (type == null) {
            sendError(ctx, "Attribute type '$typeId' not found. Use /tag to open the menu.")
            return 0
        }

        val value = type.getValue(valueId)
        if (value == null) {
            sendError(ctx, "Value '$valueId' not found in type '$typeId'.")
            return 0
        }

        if (PlayerTagModel.setPlayerAttribute(uuid, playerName, typeId, valueId)) {
            sendSuccess(ctx, "Marked $playerName as ${type.displayName}: ${value.displayName}")
            return 1
        } else {
            sendError(ctx, "Failed to mark player.")
            return 0
        }
    }

    private fun unmarkPlayer(ctx: CommandContext<FabricClientCommandSource>): Int {
        val playerName = StringArgumentType.getString(ctx, "player")
        val player = PlayerTagModel.getPlayerByName(playerName)

        if (player == null) {
            sendError(ctx, "Player '$playerName' is not tagged.")
            return 0
        }

        if (PlayerTagModel.untagPlayer(player.uuid)) {
            sendSuccess(ctx, "Removed all tags from $playerName")
            return 1
        } else {
            sendError(ctx, "Failed to untag player.")
            return 0
        }
    }

    // ============== Utility Functions ==============

    private fun getPlayerUuid(name: String): String? {
        val entry = mc.networkHandler?.playerList?.find {
            it.profile.name.equals(name, ignoreCase = true)
        }
        return entry?.profile?.id?.toString()
    }

    private fun sendSuccess(ctx: CommandContext<FabricClientCommandSource>, message: String) {
        ctx.source.sendFeedback(Text.literal("§a[Tag] §f$message"))
    }

    private fun sendError(ctx: CommandContext<FabricClientCommandSource>, message: String) {
        ctx.source.sendError(Text.literal("§c[Tag] §f$message"))
    }
}
