package xyz.nim.civutils.features.utilities

import net.minecraft.client.Minecraft
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.feature.Category
import xyz.nim.civutils.core.feature.ConfigCategory
import xyz.nim.civutils.core.feature.Feature
import xyz.nim.civutils.core.keybind.KeybindManager
import xyz.nim.civutils.gui.screens.HandbookScreen

/**
 * Feature that provides access to the in-game handbook.
 * Handles keybind to open the handbook screen.
 *
 * This feature is always active but respects the userEnabled setting
 * to determine if the keybind should work.
 */
@ConfigCategory(Category.UTILITIES)
class HandbookFeature : Feature() {

    override val description = "In-game reference handbook with game knowledge"

    // Always keep this feature active (events registered)
    override val canBeDisabled = false

    private val mc: Minecraft get() = Minecraft.getInstance()

    @Subscribe
    fun onTick(event: ClientTickEvent) {
        // Only handle keybind when no screen is open
        if (mc.screen != null) return

        if (KeybindManager.openHandbook.consumeClick()) {
            // Only open if the feature is enabled in settings
            if (userEnabled.value) {
                mc.setScreen(HandbookScreen())
            }
        }
    }
}
