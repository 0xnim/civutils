package xyz.nim.civutils.features.utilities

import net.minecraft.client.MinecraftClient
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
 * Automatically sends /sit command after being AFK to prevent hunger loss.
 */
@ConfigCategory(Category.UTILITIES)
class AutoSitFeature : Feature() {

    override val description = "Automatically /sit after being AFK to prevent hunger loss"

    /**
     * Time in seconds before auto-sitting (default 2 minutes = 120 seconds).
     */
    @Persisted
    val afkTimeSeconds = intConfig(default = 120, min = 30, max = 600)

    /**
     * Whether to show a chat message when auto-sitting.
     */
    @Persisted
    val showChatMessage = Config(defaultValue = true)

    private val mc: MinecraftClient get() = MinecraftClient.getInstance()

    private var lastActivityTime: Long = System.currentTimeMillis()
    private var lastPlayerX: Double = 0.0
    private var lastPlayerY: Double = 0.0
    private var lastPlayerZ: Double = 0.0
    private var lastPlayerYaw: Float = 0f
    private var lastPlayerPitch: Float = 0f
    private var hasSentSitCommand: Boolean = false

    override fun onEnable() {
        resetActivity()
        CivutilsMod.logger.info("AutoSitFeature enabled with ${afkTimeSeconds.value}s timeout")
    }

    override fun onDisable() {
        // Nothing to clean up
    }

    @Subscribe
    fun onClientTick(event: ClientTickEvent) {
        val player = mc.player ?: return

        // Check if player has moved or rotated
        val hasMoved = hasPlayerMoved()

        if (hasMoved) {
            resetActivity()
        } else {
            // Check if AFK time exceeded
            val afkDurationMs = System.currentTimeMillis() - lastActivityTime
            val afkThresholdMs = afkTimeSeconds.value * 1000L

            if (afkDurationMs >= afkThresholdMs && !hasSentSitCommand) {
                sendSitCommand()
            }
        }

        // Update last known position/rotation
        lastPlayerX = PlayerModel.x
        lastPlayerY = PlayerModel.y
        lastPlayerZ = PlayerModel.z
        lastPlayerYaw = PlayerModel.yaw
        lastPlayerPitch = PlayerModel.pitch
    }

    private fun hasPlayerMoved(): Boolean {
        val positionThreshold = 0.01
        val rotationThreshold = 0.5f

        val deltaX = kotlin.math.abs(PlayerModel.x - lastPlayerX)
        val deltaY = kotlin.math.abs(PlayerModel.y - lastPlayerY)
        val deltaZ = kotlin.math.abs(PlayerModel.z - lastPlayerZ)
        val deltaYaw = kotlin.math.abs(PlayerModel.yaw - lastPlayerYaw)
        val deltaPitch = kotlin.math.abs(PlayerModel.pitch - lastPlayerPitch)

        return deltaX > positionThreshold ||
                deltaY > positionThreshold ||
                deltaZ > positionThreshold ||
                deltaYaw > rotationThreshold ||
                deltaPitch > rotationThreshold
    }

    private fun resetActivity() {
        lastActivityTime = System.currentTimeMillis()
        hasSentSitCommand = false
    }

    private fun sendSitCommand() {
        val player = mc.player ?: return
        val networkHandler = mc.networkHandler ?: return

        // Send /sit command
        networkHandler.sendChatCommand("sit")
        hasSentSitCommand = true

        if (showChatMessage.value) {
            mc.inGameHud.chatHud.addMessage(
                net.minecraft.text.Text.literal("§7[CivUtils] §aAuto-sitting due to AFK")
            )
        }

        CivutilsMod.logger.debug("Auto-sit triggered after ${afkTimeSeconds.value}s of inactivity")
    }
}
