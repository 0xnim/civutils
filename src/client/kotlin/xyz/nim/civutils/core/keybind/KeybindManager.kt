package xyz.nim.civutils.core.keybind

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import org.lwjgl.glfw.GLFW
import xyz.nim.civutils.core.CivutilsMod

/**
 * Manages keybindings for the mod.
 */
object KeybindManager {
    private val keybindings = mutableMapOf<String, KeyMapping>()

    /**
     * Keybind to open the config GUI.
     */
    lateinit var openConfigGui: KeyMapping
        private set

    /**
     * Keybind to open the overlay editor.
     */
    lateinit var openOverlayEditor: KeyMapping
        private set

    /**
     * Keybind to open quick tag popup for player under crosshair.
     */
    lateinit var quickTagPopup: KeyMapping
        private set

    /**
     * Keybind to instantly mark player under crosshair as hostile.
     */
    lateinit var instantHostile: KeyMapping
        private set

    /**
     * Keybind to instantly mark player under crosshair as trusted (friendly).
     */
    lateinit var instantFriendly: KeyMapping
        private set

    /**
     * Register all keybindings. Called during initialization.
     */
    fun register() {
        openConfigGui = registerKeybind(
            id = "open_config",
            key = GLFW.GLFW_KEY_RIGHT_SHIFT,
            category = "civutils.keybind.category"
        )

        openOverlayEditor = registerKeybind(
            id = "open_overlay_editor",
            key = GLFW.GLFW_KEY_UNKNOWN, // Unbound by default
            category = "civutils.keybind.category"
        )

        // Player tagging keybinds
        quickTagPopup = registerMouseKeybind(
            id = "quick_tag_popup",
            button = GLFW.GLFW_MOUSE_BUTTON_MIDDLE, // Middle mouse button
            category = "civutils.keybind.category"
        )

        instantHostile = registerKeybind(
            id = "instant_hostile",
            key = GLFW.GLFW_KEY_UNKNOWN, // Unbound by default
            category = "civutils.keybind.category"
        )

        instantFriendly = registerKeybind(
            id = "instant_friendly",
            key = GLFW.GLFW_KEY_UNKNOWN, // Unbound by default
            category = "civutils.keybind.category"
        )

        CivutilsMod.logger.info("Registered ${keybindings.size} keybindings")
    }

    /**
     * Register a single keybinding (keyboard).
     */
    private fun registerKeybind(id: String, key: Int, category: String): KeyMapping {
        val keybind = KeyMapping(
            "civutils.keybind.$id",
            InputConstants.Type.KEYSYM,
            key,
            category
        )
        KeyBindingHelper.registerKeyBinding(keybind)
        keybindings[id] = keybind
        return keybind
    }

    /**
     * Register a mouse button keybinding.
     */
    private fun registerMouseKeybind(id: String, button: Int, category: String): KeyMapping {
        val keybind = KeyMapping(
            "civutils.keybind.$id",
            InputConstants.Type.MOUSE,
            button,
            category
        )
        KeyBindingHelper.registerKeyBinding(keybind)
        keybindings[id] = keybind
        return keybind
    }

    /**
     * Check if a keybind was just pressed (for use in tick handler).
     */
    fun wasPressed(keybind: KeyMapping): Boolean {
        return keybind.consumeClick()
    }
}
