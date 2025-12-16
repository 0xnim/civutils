package xyz.nim.civutils.core.keybind

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
import net.minecraft.client.option.KeyBinding
import net.minecraft.client.util.InputUtil
import org.lwjgl.glfw.GLFW
import xyz.nim.civutils.core.CivutilsMod

/**
 * Manages keybindings for the mod.
 */
object KeybindManager {
    private val keybindings = mutableMapOf<String, KeyBinding>()

    /**
     * Keybind to open the config GUI.
     */
    lateinit var openConfigGui: KeyBinding
        private set

    /**
     * Keybind to open the overlay editor.
     */
    lateinit var openOverlayEditor: KeyBinding
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

        CivutilsMod.logger.info("Registered ${keybindings.size} keybindings")
    }

    /**
     * Register a single keybinding.
     */
    private fun registerKeybind(id: String, key: Int, category: String): KeyBinding {
        val keybind = KeyBinding(
            "civutils.keybind.$id",
            InputUtil.Type.KEYSYM,
            key,
            category
        )
        KeyBindingHelper.registerKeyBinding(keybind)
        keybindings[id] = keybind
        return keybind
    }

    /**
     * Check if a keybind was just pressed (for use in tick handler).
     */
    fun wasPressed(keybind: KeyBinding): Boolean {
        return keybind.wasPressed()
    }
}
