package xyz.nim.civutils.features.players

import net.minecraft.client.Minecraft
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.booleanConfig
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.feature.Category
import xyz.nim.civutils.core.feature.ConfigCategory
import xyz.nim.civutils.core.feature.Feature
import xyz.nim.civutils.data.playertag.LocationSnapshot
import xyz.nim.civutils.models.PlayerTagModel
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.BooleanConfig
import xyz.nim.lib.mc121.compat.McCompat

/**
 * Feature for tagging and tracking players with custom attributes.
 */
@ConfigCategory(Category.PLAYERS)
class PlayerTagFeature : Feature() {

    override val displayName = "Player Tags"
    override val description = "Tag and track players with custom attributes"

    private val mc: Minecraft get() = Minecraft.getInstance()

    val enableNameTags: BooleanConfig = booleanConfig(
        name = "enableNameTags",
        default = true,
        displayName = "Enable Name Tags",
        comment = "Style player name tags"
    )

    val enableTabList: BooleanConfig = booleanConfig(
        name = "enableTabList",
        default = true,
        displayName = "Enable Tab List",
        comment = "Style player names in tab list"
    )

    val enableIconsAboveHead: BooleanConfig = booleanConfig(
        name = "enableIconsAboveHead",
        default = true,
        displayName = "Enable Icons Above Head",
        comment = "Show icons above player heads"
    )

    val autoAddDefaults: BooleanConfig = booleanConfig(
        name = "autoAddDefaults",
        default = true,
        displayName = "Auto Add Defaults",
        comment = "Automatically add default attribute types"
    )

    init {
        enableNameTags.onValueChanged { onConfigUpdate(enableNameTags) }
        enableTabList.onValueChanged { onConfigUpdate(enableTabList) }
        enableIconsAboveHead.onValueChanged { onConfigUpdate(enableIconsAboveHead) }
        autoAddDefaults.onValueChanged { onConfigUpdate(autoAddDefaults) }
    }

    override fun getConfigs(): List<ConfigOption<*>> = listOf(
        userEnabled, enableNameTags, enableTabList, enableIconsAboveHead, autoAddDefaults
    )

    private var tickCounter = 0

    override fun onEnable() {
        CivutilsMod.logger.info("PlayerTagFeature enabled")
    }

    override fun onDisable() {
        PlayerTagModel.saveIfDirty()
        CivutilsMod.logger.info("PlayerTagFeature disabled")
    }

    override fun onConfigUpdate(config: ConfigOption<*>) {
        CivutilsMod.configManager.markDirty()
    }

    @Subscribe
    fun onClientTick(event: ClientTickEvent) {
        if (!PlayerTagModel.hasData()) return

        tickCounter++

        if (tickCounter == 20 && autoAddDefaults.value) {
            if (PlayerTagModel.getAttributeTypes().isEmpty()) {
                PlayerTagModel.addDefaultAttributeTypes()
                CivutilsMod.logger.info("Added default attribute types")
            }
        }

        if (tickCounter % 100 == 0) {
            updateTrackedPlayers()
        }

        if (tickCounter % 1200 == 0) {
            PlayerTagModel.saveIfDirty()
        }
    }

    private fun updateTrackedPlayers() {
        val level = mc.level ?: return
        val player = mc.player ?: return

        for (entity in level.players()) {
            if (entity == player) continue

            val uuid = entity.stringUUID
            val tagged = PlayerTagModel.getPlayer(uuid) ?: continue

            val location = LocationSnapshot(
                x = entity.blockX,
                y = entity.blockY,
                z = entity.blockZ,
                dimension = McCompat.get().getResourceKeyId(level.dimension()),
                serverAddress = ""
            )

            PlayerTagModel.updatePlayerLastSeen(entity.gameProfile.name, location, uuid)
        }
    }

    companion object {
        fun getInstance(): PlayerTagFeature? {
            return CivutilsMod.featureManager.getFeature<PlayerTagFeature>()
        }
    }
}
