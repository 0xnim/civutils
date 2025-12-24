package xyz.nim.civutils.features.players

import net.minecraft.client.MinecraftClient
import net.minecraft.entity.player.PlayerEntity
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.config.Persisted
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.feature.Category
import xyz.nim.civutils.core.feature.ConfigCategory
import xyz.nim.civutils.core.feature.Feature
import xyz.nim.civutils.data.playertag.LocationSnapshot
import xyz.nim.civutils.models.PlayerTagModel

/**
 * Feature for tagging and tracking players with custom attributes.
 * Allows marking players with multiple attribute types (class, trust level, etc.)
 */
@ConfigCategory(Category.PLAYERS)
class PlayerTagFeature : Feature() {

    override val displayName = "Player Tags"
    override val description = "Tag and track players with custom attributes"

    private val mc: MinecraftClient get() = MinecraftClient.getInstance()

    /**
     * Enable styling of player name tags.
     */
    @Persisted
    val enableNameTags = Config(defaultValue = true)

    /**
     * Enable styling of player names in the tab list.
     */
    @Persisted
    val enableTabList = Config(defaultValue = true)

    /**
     * Enable icons above player heads.
     */
    @Persisted
    val enableIconsAboveHead = Config(defaultValue = true)

    /**
     * Automatically add default attribute types when joining a new server.
     */
    @Persisted
    val autoAddDefaults = Config(defaultValue = true)

    /**
     * Tick counter for periodic updates.
     */
    private var tickCounter = 0

    override fun onEnable() {
        CivutilsMod.logger.info("PlayerTagFeature enabled")
    }

    override fun onDisable() {
        PlayerTagModel.saveIfDirty()
        CivutilsMod.logger.info("PlayerTagFeature disabled")
    }

    @Subscribe
    fun onClientTick(event: ClientTickEvent) {
        if (!PlayerTagModel.hasData()) return

        tickCounter++

        // Add default attribute types on first tick after joining a server
        if (tickCounter == 20 && autoAddDefaults.value) {
            if (PlayerTagModel.getAttributeTypes().isEmpty()) {
                PlayerTagModel.addDefaultAttributeTypes()
                CivutilsMod.logger.info("Added default attribute types")
            }
        }

        // Update last seen for tagged players every 5 seconds (100 ticks)
        if (tickCounter % 100 == 0) {
            updateTrackedPlayers()
        }

        // Periodic save every minute (1200 ticks)
        if (tickCounter % 1200 == 0) {
            PlayerTagModel.saveIfDirty()
        }
    }

    /**
     * Update the last seen location for all tagged players in the world.
     */
    private fun updateTrackedPlayers() {
        val world = mc.world ?: return
        val player = mc.player ?: return

        for (entity in world.players) {
            if (entity == player) continue

            val uuid = entity.uuidAsString
            val tagged = PlayerTagModel.getPlayer(uuid) ?: continue

            val location = LocationSnapshot(
                x = entity.blockX,
                y = entity.blockY,
                z = entity.blockZ,
                dimension = world.registryKey.value.toString(),
                serverAddress = ""
            )

            PlayerTagModel.updatePlayerLastSeen(uuid, entity.gameProfile.name, location)
        }
    }

    companion object {
        /**
         * Get the singleton instance of PlayerTagFeature.
         */
        fun getInstance(): PlayerTagFeature? {
            return CivutilsMod.featureManager.getFeature<PlayerTagFeature>()
        }
    }
}
