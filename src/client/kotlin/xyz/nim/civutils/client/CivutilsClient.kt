package xyz.nim.civutils.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.EntityHitResult
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.HudRenderEvent
import xyz.nim.civutils.core.event.WorldJoinEvent
import xyz.nim.civutils.core.event.WorldLeaveEvent
import xyz.nim.civutils.core.keybind.KeybindManager
import xyz.nim.civutils.core.network.CivChannelManager
import xyz.nim.civutils.features.players.PlayerTagCommands
import xyz.nim.civutils.features.utilities.AutoSitFeature
import xyz.nim.civutils.features.utilities.HandbookFeature
import xyz.nim.civutils.gui.screens.ConfigScreen
import xyz.nim.civutils.gui.screens.OverlayEditorScreen
import xyz.nim.civutils.gui.screens.QuickTagScreen
import xyz.nim.civutils.models.BossBarModel
import xyz.nim.civutils.models.ClassModel
import xyz.nim.civutils.models.HandbookModel
import xyz.nim.civutils.models.PlayerModel
import xyz.nim.civutils.models.PlayerTagModel
import xyz.nim.civutils.models.ServerFeaturesModel
import xyz.nim.civutils.overlays.BedHealingOverlay
import xyz.nim.civutils.overlays.BlockCountOverlay
import xyz.nim.civutils.overlays.ClassXpOverlay
import xyz.nim.civutils.overlays.CombatTimerOverlay
import xyz.nim.civutils.overlays.RepairCalculatorOverlay

/**
 * Client-side mod initializer.
 * Sets up the mod systems and registers all features, overlays, and models.
 */
class CivutilsClient : ClientModInitializer {

    private var wasInWorld = false

    override fun onInitializeClient() {
        CivutilsMod.logger.info("Initializing CivUtils client...")

        // Initialize core systems
        CivutilsMod.initialize()

        // Initialize plugin channels (must be early, before world join)
        CivChannelManager.initialize()

        // Register keybinds
        KeybindManager.register()

        // Register commands
        registerCommands()

        // Register models (must be before features that use them)
        registerModels()

        // Register features
        registerFeatures()

        // Register overlays
        registerOverlays()

        // Load configs and enable features
        CivutilsMod.configManager.loadAll()
        CivutilsMod.featureManager.initializeAll()

        // Hook into Fabric events
        registerFabricEvents()

        CivutilsMod.logger.info("CivUtils client initialized!")
    }

    /**
     * Register all models.
     */
    private fun registerModels() {
        CivutilsMod.modelManager.registerModels(
            PlayerModel,
            ClassModel,
            PlayerTagModel,
            BossBarModel,
            ServerFeaturesModel,
            HandbookModel
        )
    }

    /**
     * Register all features.
     */
    private fun registerFeatures() {
        CivutilsMod.featureManager.registerFeatures(
            AutoSitFeature(),
            HandbookFeature()
        )
    }

    /**
     * Register all commands.
     */
    private fun registerCommands() {
        PlayerTagCommands.register()
    }

    /**
     * Register all overlays.
     */
    private fun registerOverlays() {
        CivutilsMod.overlayManager.registerOverlays(
            BlockCountOverlay(),
            ClassXpOverlay(),
            RepairCalculatorOverlay(),
            CombatTimerOverlay(),
            BedHealingOverlay()
        )
    }

    /**
     * Hook into Fabric's event system to fire our events.
     */
    private fun registerFabricEvents() {
        val mc = Minecraft.getInstance()

        // Client tick - fires every tick
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Handle pending screen from commands
            PlayerTagCommands.pendingScreen?.let { screen ->
                PlayerTagCommands.pendingScreen = null
                client.setScreen(screen)
            }

            // Handle keybinds
            handleKeybinds(client)

            // Fire tick event
            CivutilsMod.eventBus.post(ClientTickEvent())

            // Check for world join/leave
            val isInWorld = client.level != null && client.player != null

            if (isInWorld && !wasInWorld) {
                CivutilsMod.eventBus.post(WorldJoinEvent())
            } else if (!isInWorld && wasInWorld) {
                CivutilsMod.eventBus.post(WorldLeaveEvent())
            }

            wasInWorld = isInWorld

            // Periodically save configs
            if (client.level != null && client.level!!.gameTime % 6000 == 0L) {
                CivutilsMod.configManager.saveIfDirty()
            }
        }

        // HUD render - fires when the HUD is being drawn
        HudRenderCallback.EVENT.register { guiGraphics, _ ->
            CivutilsMod.eventBus.post(HudRenderEvent(guiGraphics, 1.0f))
        }
    }

    /**
     * Handle keybind presses.
     */
    private fun handleKeybinds(client: Minecraft) {
        // Only process keybinds when no screen is open
        if (client.screen != null) return

        // Open config GUI
        if (KeybindManager.openConfigGui.consumeClick()) {
            client.setScreen(ConfigScreen())
        }

        // Open overlay editor
        if (KeybindManager.openOverlayEditor.consumeClick()) {
            client.setScreen(OverlayEditorScreen())
        }

        // Player tagging keybinds - require player under crosshair
        handlePlayerTagKeybinds(client)
    }

    /**
     * Handle player tagging keybinds.
     * These require the player to be looking at another player.
     */
    private fun handlePlayerTagKeybinds(client: Minecraft) {
        // Check if any tagging keybind was pressed
        val quickTagPressed = KeybindManager.quickTagPopup.consumeClick()
        val hostilePressed = KeybindManager.instantHostile.consumeClick()
        val friendlyPressed = KeybindManager.instantFriendly.consumeClick()

        if (!quickTagPressed && !hostilePressed && !friendlyPressed) return

        // Get the player under crosshair
        val targetPlayer = getPlayerUnderCrosshair(client)
        if (targetPlayer == null) {
            // No player under crosshair - could show a toast but that might be annoying
            return
        }

        val uuid = targetPlayer.stringUUID
        val name = targetPlayer.gameProfile.name

        when {
            quickTagPressed -> {
                // Open quick tag popup
                client.setScreen(QuickTagScreen(name, uuid))
            }
            hostilePressed -> {
                // Instant hostile tag
                instantTag(name, uuid, "hostile", "Hostile")
            }
            friendlyPressed -> {
                // Instant friendly tag
                instantTag(name, uuid, "trusted", "Trusted")
            }
        }
    }

    /**
     * Get the player entity under the crosshair, or null if not looking at a player.
     */
    private fun getPlayerUnderCrosshair(client: Minecraft): Player? {
        val hitResult = client.hitResult
        if (hitResult is EntityHitResult) {
            val entity = hitResult.entity
            if (entity is Player && entity != client.player) {
                return entity
            }
        }
        return null
    }

    /**
     * Apply an instant tag to a player.
     */
    private fun instantTag(name: String, uuid: String?, valueId: String, displayName: String) {
        // Ensure trust attribute type exists
        if (PlayerTagModel.getAttributeType("trust") == null) {
            PlayerTagModel.addDefaultAttributeTypes()
        }

        if (PlayerTagModel.setPlayerAttribute(name, "trust", valueId, uuid)) {
            CivutilsMod.logger.info("Tagged $name as $displayName")
        }
    }
}
