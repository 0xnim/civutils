package xyz.nim.civutils.core.config

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.loader.api.FabricLoader
import xyz.nim.civutils.core.CivutilsMod
import java.nio.file.Files
import java.nio.file.Path
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Manages loading and saving of all @Persisted configs.
 * Configs are stored as JSON in the config directory.
 */
class ConfigManager {
    private val configDir: Path = FabricLoader.getInstance().configDir.resolve(CivutilsMod.MOD_ID)
    private val configFile: Path = configDir.resolve("config.json")

    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .create()

    private val registeredOwners = mutableMapOf<String, Any>()
    private var dirty = false

    init {
        if (!Files.exists(configDir)) {
            Files.createDirectories(configDir)
        }
    }

    /**
     * Register a config owner (Feature, Overlay, etc.) to have its @Persisted configs managed.
     */
    fun registerOwner(id: String, owner: Any) {
        registeredOwners[id] = owner
        scanConfigs(id, owner)
    }

    /**
     * Unregister a config owner.
     */
    fun unregisterOwner(id: String) {
        registeredOwners.remove(id)
    }

    /**
     * Scan an owner for @Persisted configs and set up their metadata.
     */
    private fun scanConfigs(ownerId: String, owner: Any) {
        val kClass = owner::class
        for (prop in kClass.memberProperties) {
            prop.isAccessible = true
            val annotation = prop.findAnnotation<Persisted>() ?: continue
            val value = prop.getter.call(owner)

            if (value is Config<*>) {
                value.owner = owner
                value.fieldName = if (annotation.key.isNotEmpty()) annotation.key else prop.name
                CivutilsMod.logger.debug("Registered config: $ownerId.${value.fieldName}")
            }
        }
    }

    /**
     * Mark that configs have changed and need to be saved.
     */
    fun markDirty() {
        dirty = true
    }

    /**
     * Load all configs from disk.
     */
    fun loadAll() {
        if (!Files.exists(configFile)) {
            CivutilsMod.logger.info("No config file found, using defaults")
            return
        }

        try {
            val content = Files.readString(configFile)
            val root = JsonParser.parseString(content).asJsonObject

            for ((ownerId, owner) in registeredOwners) {
                val ownerJson = root.getAsJsonObject(ownerId) ?: continue
                loadOwnerConfigs(owner, ownerJson)
            }

            CivutilsMod.logger.info("Loaded configs from $configFile")
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to load configs", e)
        }
    }

    /**
     * Load configs for a specific owner from JSON.
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadOwnerConfigs(owner: Any, json: JsonObject) {
        val kClass = owner::class
        for (prop in kClass.memberProperties) {
            prop.isAccessible = true
            prop.findAnnotation<Persisted>() ?: continue
            val config = prop.getter.call(owner) as? Config<Any> ?: continue

            val key = config.fieldName
            if (!json.has(key)) continue

            try {
                val element = json.get(key)
                val value = gson.fromJson(element, config.defaultValue::class.java)
                if (value != null) {
                    config.setValueSilently(value, markEdited = true)
                }
            } catch (e: Exception) {
                CivutilsMod.logger.warn("Failed to load config $key: ${e.message}")
            }
        }
    }

    /**
     * Save all configs to disk.
     */
    fun saveAll() {
        try {
            val root = JsonObject()

            for ((ownerId, owner) in registeredOwners) {
                val ownerJson = JsonObject()
                var hasConfigs = false

                val kClass = owner::class
                for (prop in kClass.memberProperties) {
                    prop.isAccessible = true
                    prop.findAnnotation<Persisted>() ?: continue
                    val config = prop.getter.call(owner) as? Config<*> ?: continue

                    // Only save if user has edited this config
                    if (config.userEdited) {
                        ownerJson.add(config.fieldName, gson.toJsonTree(config.value))
                        hasConfigs = true
                    }
                }

                if (hasConfigs) {
                    root.add(ownerId, ownerJson)
                }
            }

            Files.writeString(configFile, gson.toJson(root))
            dirty = false
            CivutilsMod.logger.info("Saved configs to $configFile")
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to save configs", e)
        }
    }

    /**
     * Save configs if they have changed.
     */
    fun saveIfDirty() {
        if (dirty) {
            saveAll()
        }
    }

    /**
     * Get all configs for a specific owner.
     */
    fun getConfigsForOwner(owner: Any): List<Config<*>> {
        val configs = mutableListOf<Config<*>>()
        val kClass = owner::class

        for (prop in kClass.memberProperties) {
            prop.isAccessible = true
            prop.findAnnotation<Persisted>() ?: continue
            val config = prop.getter.call(owner) as? Config<*> ?: continue
            configs.add(config)
        }

        return configs
    }

    /**
     * Reset all configs for an owner to their defaults.
     */
    fun resetOwner(owner: Any) {
        for (config in getConfigsForOwner(owner)) {
            config.reset()
        }
        markDirty()
    }
}
