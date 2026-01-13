package xyz.nim.civutils.features.utilities

import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.booleanConfig
import xyz.nim.civutils.core.config.intConfig
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.feature.Category
import xyz.nim.civutils.core.feature.ConfigCategory
import xyz.nim.civutils.core.feature.Feature
import xyz.nim.civutils.models.PlayerModel
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.BooleanConfig
import xyz.nim.lib.config.options.IntegerConfig

/**
 * Automatically sends /sit command after being AFK to prevent hunger loss.
 */
@ConfigCategory(Category.UTILITIES)
class AutoSitFeature : Feature() {

    override val description = "Automatically /sit after being AFK to prevent hunger loss"

    val afkTimeSeconds: IntegerConfig = intConfig(
        name = "afkTimeSeconds",
        default = 120,
        min = 30,
        max = 600,
        displayName = "AFK Time (seconds)",
        comment = "Time in seconds before auto-sitting"
    )

    val showChatMessage: BooleanConfig = booleanConfig(
        name = "showChatMessage",
        default = true,
        displayName = "Show Chat Message",
        comment = "Show a message when auto-sitting"
    )

    init {
        afkTimeSeconds.onValueChanged { onConfigUpdate(afkTimeSeconds) }
        showChatMessage.onValueChanged { onConfigUpdate(showChatMessage) }
    }

    override fun getConfigs(): List<ConfigOption<*>> = listOf(
        userEnabled, afkTimeSeconds, showChatMessage
    )

    private val mc: Minecraft get() = Minecraft.getInstance()

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

    override fun onDisable() {}

    override fun onConfigUpdate(config: ConfigOption<*>) {
        CivutilsMod.configManager.markDirty()
    }

    @Subscribe
    fun onClientTick(event: ClientTickEvent) {
        val player = mc.player ?: return
        val hasMoved = hasPlayerMoved()

        if (hasMoved) {
            resetActivity()
        } else {
            val afkDurationMs = System.currentTimeMillis() - lastActivityTime
            val afkThresholdMs = afkTimeSeconds.value * 1000L

            if (afkDurationMs >= afkThresholdMs && !hasSentSitCommand) {
                sendSitCommand()
            }
        }

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
        val connection = mc.connection ?: return

        connection.sendCommand("sit")
        hasSentSitCommand = true

        if (showChatMessage.value) {
            mc.gui.chat.addMessage(
                Component.literal("§7[CivUtils] §aAuto-sitting due to AFK")
            )
        }

        CivutilsMod.logger.debug("Auto-sit triggered after ${afkTimeSeconds.value}s of inactivity")
    }
}
