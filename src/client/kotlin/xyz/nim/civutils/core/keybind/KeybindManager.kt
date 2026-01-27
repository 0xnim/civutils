package xyz.nim.civutils.core.keybind

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.KeyMapping
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW
import xyz.nim.civutils.core.CivutilsMod

/**
 * Manages keybindings for the mod.
 */
object KeybindManager {
    private val keybindings = mutableMapOf<String, KeyMapping>()

    /**
     * Custom keybinding category for CivUtils.
     * Registered once on first use.
     */
    private val CATEGORY: KeyMapping.Category by lazy {
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath("civutils", "keybinds"))
    }

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
     * Keybind to open the handbook.
     */
    lateinit var openHandbook: KeyMapping
        private set

    /**
     * Register all keybindings. Called during initialization.
     */
    fun register() {
        openConfigGui = registerKeybind(
            id = "open_config",
            key = GLFW.GLFW_KEY_RIGHT_SHIFT
        )

        openOverlayEditor = registerKeybind(
            id = "open_overlay_editor",
            key = GLFW.GLFW_KEY_UNKNOWN // Unbound by default
        )

        // Player tagging keybinds
        quickTagPopup = registerMouseKeybind(
            id = "quick_tag_popup",
            button = GLFW.GLFW_MOUSE_BUTTON_MIDDLE // Middle mouse button
        )

        instantHostile = registerKeybind(
            id = "instant_hostile",
            key = GLFW.GLFW_KEY_UNKNOWN // Unbound by default
        )

        instantFriendly = registerKeybind(
            id = "instant_friendly",
            key = GLFW.GLFW_KEY_UNKNOWN // Unbound by default
        )

        openHandbook = registerKeybind(
            id = "open_handbook",
            key = GLFW.GLFW_KEY_H
        )

        CivutilsMod.logger.info("Registered ${keybindings.size} keybindings")
    }

    /**
     * Register a single keybinding (keyboard).
     */
    private fun registerKeybind(id: String, key: Int): KeyMapping {
        val keybind = KeyMapping(
            "civutils.keybind.$id",
            InputConstants.Type.KEYSYM,
            key,
            CATEGORY
        )
        KeyBindingHelper.registerKeyBinding(keybind)
        keybindings[id] = keybind
        return keybind
    }

    /**
     * Register a mouse button keybinding.
     */
    private fun registerMouseKeybind(id: String, button: Int): KeyMapping {
        val keybind = KeyMapping(
            "civutils.keybind.$id",
            InputConstants.Type.MOUSE,
            button,
            CATEGORY
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
