package xyz.nim.civutils.client

import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
import net.minecraft.client.MinecraftClient
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.HudRenderEvent
import xyz.nim.civutils.core.event.WorldJoinEvent
import xyz.nim.civutils.core.event.WorldLeaveEvent
import xyz.nim.civutils.core.keybind.KeybindManager
import xyz.nim.civutils.features.combat.HealthWarningFeature
import xyz.nim.civutils.gui.screens.ConfigScreen
import xyz.nim.civutils.gui.screens.OverlayEditorScreen
import xyz.nim.civutils.models.PlayerModel
import xyz.nim.civutils.overlays.CoordinatesOverlay

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

        // Register keybinds
        KeybindManager.register()

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
            PlayerModel
            // Add more models here as you create them:
            // WorldModel,
            // CombatModel,
        )
    }

    /**
     * Register all features.
     */
    private fun registerFeatures() {
        CivutilsMod.featureManager.registerFeatures(
            HealthWarningFeature()
            // Add more features here as you create them:
            // AutoTotemFeature(),
            // ChatFilterFeature(),
        )
    }

    /**
     * Register all overlays.
     */
    private fun registerOverlays() {
        CivutilsMod.overlayManager.registerOverlays(
            CoordinatesOverlay()
            // Add more overlays here as you create them:
            // HealthOverlay(),
            // ArmorOverlay(),
        )
    }

    /**
     * Hook into Fabric's event system to fire our events.
     */
    private fun registerFabricEvents() {
        val mc = MinecraftClient.getInstance()

        // Client tick - fires every tick
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            // Handle keybinds
            handleKeybinds(client)

            // Fire tick event
            CivutilsMod.eventBus.post(ClientTickEvent())

            // Check for world join/leave
            val isInWorld = client.world != null && client.player != null

            if (isInWorld && !wasInWorld) {
                CivutilsMod.eventBus.post(WorldJoinEvent())
            } else if (!isInWorld && wasInWorld) {
                CivutilsMod.eventBus.post(WorldLeaveEvent())
            }

            wasInWorld = isInWorld

            // Periodically save configs
            if (client.world != null && client.world!!.time % 6000 == 0L) {
                CivutilsMod.configManager.saveIfDirty()
            }
        }

        // HUD render - fires when the HUD is being drawn
        HudRenderCallback.EVENT.register { drawContext, _ ->
            CivutilsMod.eventBus.post(HudRenderEvent(drawContext, 1.0f))
        }
    }

    /**
     * Handle keybind presses.
     */
    private fun handleKeybinds(client: MinecraftClient) {
        // Only process keybinds when no screen is open
        if (client.currentScreen != null) return

        // Open config GUI
        if (KeybindManager.openConfigGui.wasPressed()) {
            client.setScreen(ConfigScreen())
        }

        // Open overlay editor
        if (KeybindManager.openOverlayEditor.wasPressed()) {
            client.setScreen(OverlayEditorScreen())
        }
    }
}
