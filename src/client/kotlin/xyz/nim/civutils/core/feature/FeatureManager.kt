package xyz.nim.civutils.core.feature

import xyz.nim.civutils.core.CivutilsMod

/**
 * Manages registration and lifecycle of all features.
 */
class FeatureManager {
    private val features = mutableMapOf<String, Feature>()

    /**
     * Get all registered features.
     */
    fun getFeatures(): Collection<Feature> = features.values

    /**
     * Get a feature by its ID.
     */
    fun getFeature(id: String): Feature? = features[id]

    /**
     * Get all features in a specific category.
     */
    fun getFeaturesByCategory(category: Category): List<Feature> {
        return features.values.filter { it.category == category }
    }

    /**
     * Register a feature.
     * The feature will be enabled if userEnabled is true.
     */
    fun registerFeature(feature: Feature) {
        if (features.containsKey(feature.id)) {
            CivutilsMod.logger.warn("Feature ${feature.id} is already registered")
            return
        }

        features[feature.id] = feature
        CivutilsMod.configManager.registerOwner("feature.${feature.id}", feature)
        CivutilsMod.logger.debug("Registered feature: ${feature.id}")
    }

    /**
     * Register multiple features at once.
     */
    fun registerFeatures(vararg featuresToRegister: Feature) {
        for (feature in featuresToRegister) {
            registerFeature(feature)
        }
    }

    /**
     * Initialize all registered features.
     * Call this after configs have been loaded.
     */
    fun initializeAll() {
        CivutilsMod.logger.info("Initializing ${features.size} features...")

        for (feature in features.values) {
            if (feature.userEnabled.value) {
                enableFeature(feature)
            }
        }

        val enabled = features.values.count { it.enabled }
        CivutilsMod.logger.info("Initialized features: $enabled/${features.size} enabled")
    }

    /**
     * Enable a specific feature.
     */
    fun enableFeature(feature: Feature): Boolean {
        if (feature.enabled) return true
        return feature.enable()
    }

    /**
     * Enable a feature by ID.
     */
    fun enableFeature(id: String): Boolean {
        val feature = features[id] ?: run {
            CivutilsMod.logger.warn("Cannot enable unknown feature: $id")
            return false
        }
        return enableFeature(feature)
    }

    /**
     * Disable a specific feature.
     */
    fun disableFeature(feature: Feature): Boolean {
        if (!feature.enabled) return true
        if (!feature.canBeDisabled) {
            CivutilsMod.logger.warn("Cannot disable required feature: ${feature.id}")
            return false
        }
        return feature.disable()
    }

    /**
     * Disable a feature by ID.
     */
    fun disableFeature(id: String): Boolean {
        val feature = features[id] ?: run {
            CivutilsMod.logger.warn("Cannot disable unknown feature: $id")
            return false
        }
        return disableFeature(feature)
    }

    /**
     * Disable all features. Called during shutdown.
     */
    fun disableAll() {
        CivutilsMod.logger.info("Disabling all features...")
        for (feature in features.values) {
            if (feature.enabled) {
                feature.disable()
            }
        }
    }

    /**
     * Unregister a feature completely.
     */
    fun unregisterFeature(feature: Feature) {
        if (feature.enabled) {
            feature.disable()
        }
        features.remove(feature.id)
        CivutilsMod.configManager.unregisterOwner("feature.${feature.id}")
    }

    /**
     * Get features that are currently enabled.
     */
    fun getEnabledFeatures(): List<Feature> {
        return features.values.filter { it.enabled }
    }

    /**
     * Get features that are currently disabled.
     */
    fun getDisabledFeatures(): List<Feature> {
        return features.values.filter { !it.enabled }
    }
}
