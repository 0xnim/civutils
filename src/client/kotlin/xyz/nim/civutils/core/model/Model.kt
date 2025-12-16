package xyz.nim.civutils.core.model

import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.event.WorldJoinEvent
import xyz.nim.civutils.core.event.WorldLeaveEvent

/**
 * Base class for data models.
 *
 * Models hold game state data and update it based on events.
 * They separate data collection from display (overlays) and logic (features).
 *
 * Models are always active - they don't have an enabled state.
 * They automatically register for events when created.
 *
 * Usage:
 * ```
 * object PlayerModel : Model() {
 *     var health: Float = 0f
 *         private set
 *
 *     @Subscribe
 *     fun onTick(event: ClientTickEvent) {
 *         health = mc.player?.health ?: 0f
 *     }
 * }
 * ```
 */
abstract class Model {
    /**
     * Unique identifier for this model. Defaults to class simple name.
     */
    open val id: String get() = this::class.simpleName ?: "UnknownModel"

    /**
     * Whether this model is currently active (tracking data).
     */
    var active: Boolean = false
        private set

    /**
     * Initialize and activate this model.
     */
    internal fun activate() {
        if (active) return

        CivutilsMod.eventBus.register(this)
        active = true
        onActivate()
        CivutilsMod.logger.debug("Activated model: $id")
    }

    /**
     * Deactivate this model.
     */
    internal fun deactivate() {
        if (!active) return

        onDeactivate()
        CivutilsMod.eventBus.unregister(this)
        active = false
        CivutilsMod.logger.debug("Deactivated model: $id")
    }

    /**
     * Called when the model is activated.
     * Override to perform setup.
     */
    protected open fun onActivate() {}

    /**
     * Called when the model is deactivated.
     * Override to perform cleanup.
     */
    protected open fun onDeactivate() {}

    /**
     * Reset all tracked data.
     * Called when leaving a world.
     */
    abstract fun reset()

    /**
     * Handle world join - models should start tracking data.
     */
    @Subscribe
    fun onWorldJoin(event: WorldJoinEvent) {
        reset()
    }

    /**
     * Handle world leave - models should reset data.
     */
    @Subscribe
    fun onWorldLeave(event: WorldLeaveEvent) {
        reset()
    }

    override fun toString(): String = "Model($id, active=$active)"
}
