package xyz.nim.civutils.core.model

import xyz.nim.civutils.core.CivutilsMod

/**
 * Manages registration and lifecycle of all models.
 */
class ModelManager {
    @PublishedApi
    internal val models = mutableMapOf<String, Model>()

    /**
     * Get all registered models.
     */
    fun getModels(): Collection<Model> = models.values

    /**
     * Get a model by its ID.
     */
    fun getModel(id: String): Model? = models[id]

    /**
     * Get a model by its class.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Model> getModel(): T? {
        return models.values.find { it is T } as? T
    }

    /**
     * Register a model.
     */
    fun registerModel(model: Model) {
        if (models.containsKey(model.id)) {
            CivutilsMod.logger.warn("Model ${model.id} is already registered")
            return
        }

        models[model.id] = model
        model.activate()
        CivutilsMod.logger.debug("Registered model: ${model.id}")
    }

    /**
     * Register multiple models at once.
     */
    fun registerModels(vararg modelsToRegister: Model) {
        for (model in modelsToRegister) {
            registerModel(model)
        }
    }

    /**
     * Unregister a model.
     */
    fun unregisterModel(model: Model) {
        model.deactivate()
        models.remove(model.id)
    }

    /**
     * Reset all models (e.g., when leaving a world).
     */
    fun resetAll() {
        for (model in models.values) {
            try {
                model.reset()
            } catch (e: Exception) {
                CivutilsMod.logger.error("Error resetting model ${model.id}", e)
            }
        }
    }

    /**
     * Deactivate all models (e.g., during shutdown).
     */
    fun deactivateAll() {
        for (model in models.values) {
            model.deactivate()
        }
    }
}
