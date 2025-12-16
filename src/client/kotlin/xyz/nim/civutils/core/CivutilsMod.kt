package xyz.nim.civutils.core

import net.minecraft.client.MinecraftClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import xyz.nim.civutils.core.config.ConfigManager
import xyz.nim.civutils.core.event.EventBus
import xyz.nim.civutils.core.feature.FeatureManager
import xyz.nim.civutils.core.model.ModelManager
import xyz.nim.civutils.core.overlay.OverlayManager

/**
 * Main mod singleton - central access point for all managers and systems.
 * Similar to Wynntils' WynntilsMod but simplified for Fabric-only.
 */
object CivutilsMod {
    const val MOD_ID = "civutils"

    val logger: Logger = LoggerFactory.getLogger(MOD_ID)

    val mc: MinecraftClient get() = MinecraftClient.getInstance()

    // Core managers
    lateinit var eventBus: EventBus
        private set
    lateinit var configManager: ConfigManager
        private set
    lateinit var featureManager: FeatureManager
        private set
    lateinit var overlayManager: OverlayManager
        private set
    lateinit var modelManager: ModelManager
        private set

    private var initialized = false

    /**
     * Initialize all core systems. Called from CivutilsClient.
     */
    fun initialize() {
        if (initialized) {
            logger.warn("CivutilsMod already initialized!")
            return
        }

        logger.info("Initializing CivutilsMod...")

        // Phase 1: Core systems (order matters!)
        eventBus = EventBus()
        configManager = ConfigManager()
        modelManager = ModelManager()

        // Phase 2: Feature and overlay systems (depend on core)
        featureManager = FeatureManager()
        overlayManager = OverlayManager()

        // Phase 3: Load configs and enable features
        configManager.loadAll()

        initialized = true
        logger.info("CivutilsMod initialized successfully!")
    }

    /**
     * Called when the client is shutting down.
     */
    fun shutdown() {
        if (!initialized) return

        logger.info("Shutting down CivutilsMod...")
        configManager.saveAll()
        featureManager.disableAll()
        logger.info("CivutilsMod shutdown complete")
    }

    /**
     * Check if we're currently in a world/server.
     */
    fun isInGame(): Boolean = mc.world != null && mc.player != null
}
