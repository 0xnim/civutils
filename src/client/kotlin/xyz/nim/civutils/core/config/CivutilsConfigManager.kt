package xyz.nim.civutils.core.config

import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.lib.config.ConfigCategory
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.ModConfig
import xyz.nim.lib.config.serialization.ConfigSerializer
import xyz.nim.lib.util.ConfigManager as NlibConfigManager
import com.google.gson.GsonBuilder

/**
 * Manages config persistence using nlib's ModConfig system.
 *
 * Features and Overlays register their configs dynamically at runtime,
 * so this manager builds the ModConfig incrementally and handles
 * save/load operations.
 */
class CivutilsConfigManager {

    private val nlibConfigManager = NlibConfigManager(CivutilsMod.MOD_ID, GsonBuilder().setPrettyPrinting().create())
    private val serializer = ConfigSerializer(nlibConfigManager.gson)

    // Store configs by owner ID for dynamic registration
    private val ownerConfigs = mutableMapOf<String, MutableList<ConfigOption<*>>>()
    private var dirty = false

    // Built ModConfig (rebuilt when new configs are registered)
    private var modConfig: ModConfig? = null
    private var configLoaded = false

    /**
     * Register configs for an owner (Feature or Overlay).
     * Call this after creating all ConfigOptions for the owner.
     */
    fun registerOwner(ownerId: String, configs: List<ConfigOption<*>>) {
        if (configs.isEmpty()) return

        ownerConfigs[ownerId] = configs.toMutableList()
        rebuildModConfig()

        // If config was already loaded, load values for this owner
        if (configLoaded) {
            loadAll()
        }

        CivutilsMod.logger.debug("Registered ${configs.size} configs for $ownerId")
    }

    /**
     * Unregister an owner's configs.
     */
    fun unregisterOwner(ownerId: String) {
        ownerConfigs.remove(ownerId)
        rebuildModConfig()
    }

    /**
     * Rebuild the ModConfig from current registered owners.
     */
    private fun rebuildModConfig() {
        val builder = ModConfig.create(CivutilsMod.MOD_ID)

        for ((ownerId, configs) in ownerConfigs) {
            val categoryBuilder = ConfigCategory.create(ownerId)
            for (config in configs) {
                categoryBuilder.addOption(config)
            }
            builder.addCategory(categoryBuilder.build())
        }

        modConfig = builder.build()
    }

    /**
     * Mark configs as dirty (needs saving).
     */
    fun markDirty() {
        dirty = true
    }

    /**
     * Load all configs from disk.
     */
    fun loadAll() {
        val config = modConfig ?: return

        try {
            serializer.load(config, nlibConfigManager)
            configLoaded = true
            CivutilsMod.logger.info("Loaded configs from disk")
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load configs", e)
        }
    }

    /**
     * Save all configs to disk.
     */
    fun saveAll() {
        val config = modConfig ?: return

        try {
            serializer.save(config, nlibConfigManager)
            dirty = false
            CivutilsMod.logger.info("Saved configs to disk")
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to save configs", e)
        }
    }

    /**
     * Save configs if dirty.
     */
    fun saveIfDirty() {
        if (dirty) {
            saveAll()
        }
    }

    /**
     * Get all configs for an owner.
     */
    fun getConfigsForOwner(ownerId: String): List<ConfigOption<*>> {
        return ownerConfigs[ownerId]?.toList() ?: emptyList()
    }

    /**
     * Get all registered config options.
     */
    fun getAllConfigs(): List<ConfigOption<*>> {
        return ownerConfigs.values.flatten()
    }

    /**
     * Reset all configs for an owner to defaults.
     */
    fun resetOwner(ownerId: String) {
        ownerConfigs[ownerId]?.forEach { it.resetToDefault() }
        markDirty()
    }
}
