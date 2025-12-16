package xyz.nim.civutils.features.combat

import net.minecraft.client.MinecraftClient
import net.minecraft.sound.SoundEvents
import net.minecraft.text.Text
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.config.Persisted
import xyz.nim.civutils.core.config.intConfig
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.feature.Category
import xyz.nim.civutils.core.feature.ConfigCategory
import xyz.nim.civutils.core.feature.Feature
import xyz.nim.civutils.models.PlayerModel

/**
 * Example feature: Warns the player when their health is low.
 *
 * Demonstrates:
 * - @ConfigCategory annotation for organization
 * - @Persisted configs with validation
 * - Event subscription with @Subscribe
 * - Using a Model for data
 * - Lifecycle hooks (onEnable/onDisable)
 */
@ConfigCategory(Category.COMBAT)
class HealthWarningFeature : Feature() {

    override val description = "Warns you when your health drops below a threshold"

    /**
     * Health threshold percentage (0-100) to trigger warning.
     */
    @Persisted
    val healthThreshold = intConfig(default = 30, min = 5, max = 95)

    /**
     * Whether to play a warning sound.
     */
    @Persisted
    val playSound = Config(defaultValue = true)

    /**
     * Whether to show a chat message warning.
     */
    @Persisted
    val showChatMessage = Config(defaultValue = true)

    /**
     * Cooldown between warnings in ticks (20 ticks = 1 second).
     */
    @Persisted
    val warningCooldown = intConfig(default = 100, min = 20, max = 600)

    private val mc: MinecraftClient get() = MinecraftClient.getInstance()

    private var lastWarningTime: Long = 0
    private var wasAboveThreshold: Boolean = true

    override fun onEnable() {
        // Reset state when enabled
        lastWarningTime = 0
        wasAboveThreshold = true
        CivutilsMod.logger.info("HealthWarningFeature enabled with threshold: ${healthThreshold.value}%")
    }

    override fun onDisable() {
        // Nothing to clean up
    }

    override fun onConfigUpdate(config: Config<*>) {
        if (config === healthThreshold) {
            CivutilsMod.logger.debug("Health threshold updated to: ${healthThreshold.value}%")
        }
    }

    @Subscribe
    fun onClientTick(event: ClientTickEvent) {
        val player = mc.player ?: return

        // Use the PlayerModel to get health data
        val healthPercent = PlayerModel.healthPercent

        val isAboveThreshold = healthPercent > healthThreshold.value
        val currentTime = System.currentTimeMillis()
        val cooldownMs = warningCooldown.value * 50L // Convert ticks to ms

        // Only warn when crossing the threshold (going from above to below)
        // and respecting cooldown
        if (!isAboveThreshold && wasAboveThreshold) {
            if (currentTime - lastWarningTime >= cooldownMs) {
                triggerWarning(healthPercent)
                lastWarningTime = currentTime
            }
        }

        wasAboveThreshold = isAboveThreshold
    }

    private fun triggerWarning(currentHealth: Int) {
        val player = mc.player ?: return

        if (playSound.value) {
            player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.0f, 0.5f)
        }

        if (showChatMessage.value) {
            mc.inGameHud.chatHud.addMessage(
                Text.literal("§c§l⚠ Low Health Warning! §r§7(${currentHealth}%)")
            )
        }

        CivutilsMod.logger.debug("Health warning triggered at $currentHealth%")
    }
}
