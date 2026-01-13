package xyz.nim.civutils.core.feature

import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.booleanConfig
import xyz.nim.civutils.core.config.value
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.BooleanConfig

/**
 * Categories for organizing features in the config GUI.
 */
enum class Category {
    COMBAT,
    CHAT,
    INVENTORY,
    MAP,
    OVERLAYS,
    PLAYERS,
    UTILITIES,
    DEBUG
}

/**
 * Annotation to specify which category a feature belongs to.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class ConfigCategory(val category: Category)

/**
 * Base class for all features in the mod.
 *
 * Features are modular pieces of functionality that can be enabled/disabled.
 * They automatically register to the event bus when enabled and unregister when disabled.
 *
 * Usage:
 * ```
 * @ConfigCategory(Category.COMBAT)
 * class MyFeature : Feature() {
 *     val myConfig = intConfig("myConfig", 100, min = 0, max = 200)
 *
 *     override fun getConfigs() = listOf(super.getConfigs(), myConfig).flatten()
 *
 *     @Subscribe
 *     fun onTick(event: ClientTickEvent) {
 *         // handle event
 *     }
 *
 *     override fun onEnable() {
 *         // setup
 *     }
 *
 *     override fun onDisable() {
 *         // cleanup
 *     }
 * }
 * ```
 */
abstract class Feature {
    /**
     * Unique identifier for this feature. Defaults to class simple name.
     */
    open val id: String get() = this::class.simpleName ?: "UnknownFeature"

    /**
     * Display name for the config GUI.
     */
    open val displayName: String get() = id.replace(Regex("([A-Z])"), " $1").trim()

    /**
     * Description shown in the config GUI.
     */
    open val description: String = ""

    /**
     * Whether this feature is currently enabled and active.
     */
    var enabled: Boolean = false
        private set

    /**
     * User preference for whether this feature should be enabled.
     * This is persisted to config.
     */
    val userEnabled: BooleanConfig = booleanConfig(
        name = "userEnabled",
        default = true,
        displayName = "Enabled",
        comment = "Whether this feature is enabled"
    ).also { config ->
        config.onValueChanged { newValue ->
            onConfigUpdate(config)
            CivutilsMod.configManager.markDirty()
        }
    }

    /**
     * Whether this feature can be disabled by the user.
     * Some core features may be always-on.
     */
    open val canBeDisabled: Boolean = true

    /**
     * Get the category for this feature.
     */
    val category: Category
        get() = this::class.annotations
            .filterIsInstance<ConfigCategory>()
            .firstOrNull()?.category ?: Category.UTILITIES

    /**
     * Enable this feature.
     * Registers event handlers and calls onEnable().
     */
    internal fun enable(): Boolean {
        if (enabled) return true

        return try {
            CivutilsMod.eventBus.register(this)
            enabled = true
            onEnable()
            CivutilsMod.logger.info("Enabled feature: $id")
            true
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to enable feature $id", e)
            CivutilsMod.eventBus.unregister(this)
            enabled = false
            false
        }
    }

    /**
     * Disable this feature.
     * Unregisters event handlers and calls onDisable().
     */
    internal fun disable(): Boolean {
        if (!enabled) return true

        return try {
            onDisable()
            CivutilsMod.eventBus.unregister(this)
            enabled = false
            CivutilsMod.logger.info("Disabled feature: $id")
            true
        } catch (e: Exception) {
            CivutilsMod.logger.error("Failed to disable feature $id cleanly", e)
            CivutilsMod.eventBus.unregister(this)
            enabled = false
            false
        }
    }

    /**
     * Toggle this feature on/off based on user preference.
     */
    fun setUserEnabled(value: Boolean) {
        if (!canBeDisabled && !value) {
            CivutilsMod.logger.warn("Cannot disable feature $id - it is required")
            return
        }

        userEnabled.value = value

        if (value) {
            CivutilsMod.featureManager.enableFeature(this)
        } else {
            CivutilsMod.featureManager.disableFeature(this)
        }
    }

    /**
     * Called when the feature is enabled.
     * Override to perform setup.
     */
    protected open fun onEnable() {}

    /**
     * Called when the feature is disabled.
     * Override to perform cleanup.
     */
    protected open fun onDisable() {}

    /**
     * Called when any config for this feature changes.
     * Override to respond to config changes.
     */
    open fun onConfigUpdate(config: ConfigOption<*>) {}

    /**
     * Get all configs for this feature.
     * Override to include feature-specific configs.
     */
    open fun getConfigs(): List<ConfigOption<*>> = listOf(userEnabled)

    /**
     * Register this feature's configs with the config manager.
     * Called by FeatureManager after the feature is constructed.
     */
    internal fun registerConfigs() {
        val configs = getConfigs()
        if (configs.isNotEmpty()) {
            CivutilsMod.configManager.registerOwner(id, configs)
        }
    }

    override fun toString(): String = "Feature($id, enabled=$enabled)"
}
