package xyz.nim.civutils.features.utilities

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.core.event.ClientTickEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.feature.Category
import xyz.nim.civutils.core.feature.ConfigCategory
import xyz.nim.civutils.core.feature.Feature
import xyz.nim.civutils.core.keybind.KeybindManager
import xyz.nim.civutils.gui.screens.HandbookScreen
import xyz.nim.civutils.mixin.client.AbstractContainerScreenAccessor
import xyz.nim.civutils.utils.ItemMatcher

/**
 * Feature that provides access to the in-game handbook.
 * Handles keybind to open the handbook screen.
 *
 * Features:
 * - Press H with no screen open: Opens handbook to default page
 * - Press H while hovering over an item in inventory: Opens handbook to that item's page
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
        val screen = mc.screen

        // Handle H key in inventory screens
        if (screen is AbstractContainerScreen<*>) {
            if (KeybindManager.openHandbook.consumeClick()) {
                handleInventoryHandbookKey(screen)
                return
            }
        }

        // Only handle keybind when no screen is open
        if (screen != null) return

        if (KeybindManager.openHandbook.consumeClick()) {
            // Only open if the feature is enabled in settings
            if (userEnabled.value) {
                mc.setScreen(HandbookScreen())
            }
        }
    }

    /**
     * Handle the handbook keybind when pressed in an inventory screen.
     * If hovering over an item, opens the handbook to that item's page.
     */
    private fun handleInventoryHandbookKey(screen: AbstractContainerScreen<*>) {
        if (!userEnabled.value) return

        // Get the slot under the mouse cursor using accessor mixin
        val accessor = screen as AbstractContainerScreenAccessor
        val hoveredSlot = accessor.hoveredSlot
        if (hoveredSlot == null || !hoveredSlot.hasItem()) {
            // No item hovered - just open handbook
            mc.setScreen(HandbookScreen())
            return
        }

        val stack = hoveredSlot.item
        if (stack.isEmpty) {
            mc.setScreen(HandbookScreen())
            return
        }

        // Find matching page for this item
        val pageId = ItemMatcher.getPageIdForItem(stack)
        if (pageId != null) {
            mc.setScreen(HandbookScreen(pageId))
        } else {
            // No page found - just open handbook to default
            mc.setScreen(HandbookScreen())
        }
    }
}
