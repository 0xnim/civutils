package xyz.nim.civutils.models

import net.minecraft.client.Minecraft
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.model.Model

/**
 * Example model: Tracks player state data.
 *
 * Demonstrates:
 * - Using object for singleton pattern
 * - Tracking game state with events
 * - Providing computed properties
 * - Resetting on world leave
 *
 * Usage from features/overlays:
 * ```
 * val health = PlayerModel.health
 * val healthPercent = PlayerModel.healthPercent
 * ```
 */
object PlayerModel : Model() {

    private val mc: Minecraft get() = Minecraft.getInstance()

    // ============================================
    // Health Data
    // ============================================

    /** Current health (0-20 typically) */
    var health: Float = 0f
        private set

    /** Maximum health */
    var maxHealth: Float = 20f
        private set

    /** Health as a percentage (0-100) */
    val healthPercent: Int
        get() = if (maxHealth > 0) ((health / maxHealth) * 100).toInt() else 0

    /** Whether the player is dead */
    val isDead: Boolean
        get() = health <= 0

    // ============================================
    // Position Data
    // ============================================

    /** Current X coordinate */
    var x: Double = 0.0
        private set

    /** Current Y coordinate */
    var y: Double = 0.0
        private set

    /** Current Z coordinate */
    var z: Double = 0.0
        private set

    /** Block X coordinate */
    val blockX: Int get() = x.toInt()

    /** Block Y coordinate */
    val blockY: Int get() = y.toInt()

    /** Block Z coordinate */
    val blockZ: Int get() = z.toInt()

    // ============================================
    // Movement Data
    // ============================================

    /** Current yaw (horizontal rotation) */
    var yaw: Float = 0f
        private set

    /** Current pitch (vertical rotation) */
    var pitch: Float = 0f
        private set

    /** Cardinal direction the player is facing */
    val facingDirection: String
        get() {
            val normalizedYaw = ((yaw % 360) + 360) % 360
            return when {
                normalizedYaw >= 315 || normalizedYaw < 45 -> "South"
                normalizedYaw >= 45 && normalizedYaw < 135 -> "West"
                normalizedYaw >= 135 && normalizedYaw < 225 -> "North"
                else -> "East"
            }
        }

    // ============================================
    // Misc Data
    // ============================================

    /** Current food/hunger level (0-20) */
    var foodLevel: Int = 0
        private set

    /** Current experience level */
    var experienceLevel: Int = 0
        private set

    /** Experience progress to next level (0.0 - 1.0) */
    var experienceProgress: Float = 0f
        private set

    // ============================================
    // Lifecycle
    // ============================================

    override fun reset() {
        health = 0f
        maxHealth = 20f
        x = 0.0
        y = 0.0
        z = 0.0
        yaw = 0f
        pitch = 0f
        foodLevel = 0
        experienceLevel = 0
        experienceProgress = 0f
    }

    @Subscribe
    fun onClientTick(event: ClientTickEvent) {
        val player = mc.player ?: return

        // Update health
        health = player.health
        maxHealth = player.maxHealth

        // Update position
        x = player.x
        y = player.y
        z = player.z

        // Update rotation
        yaw = player.yRot
        pitch = player.xRot

        // Update misc
        foodLevel = player.foodData.foodLevel
        experienceLevel = player.experienceLevel
        experienceProgress = player.experienceProgress
    }
}
