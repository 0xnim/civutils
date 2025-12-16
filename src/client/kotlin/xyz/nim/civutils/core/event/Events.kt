package xyz.nim.civutils.core.event

import net.minecraft.client.gui.DrawContext
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.text.Text

/**
 * Common events used throughout the mod.
 * Add new events here as needed.
 */

// ============================================
// Tick Events
// ============================================

/**
 * Fired every client tick.
 */
class ClientTickEvent : Event()

/**
 * Fired every world tick (when in a world).
 */
class WorldTickEvent : Event()

// ============================================
// Render Events
// ============================================

/**
 * Fired when the HUD is being rendered.
 * Use this to render overlays.
 */
class HudRenderEvent(
    val drawContext: DrawContext,
    val tickDelta: Float
) : Event()

/**
 * Fired when the world is being rendered.
 */
class WorldRenderEvent(
    val tickDelta: Float
) : Event()

// ============================================
// Player Events
// ============================================

/**
 * Fired when the player takes damage.
 */
class PlayerDamageEvent(
    val player: PlayerEntity,
    val amount: Float,
    val newHealth: Float
) : Event()

/**
 * Fired when the player's health changes.
 */
class PlayerHealthChangeEvent(
    val player: PlayerEntity,
    val oldHealth: Float,
    val newHealth: Float
) : CancellableEvent()

/**
 * Fired when the player dies.
 */
class PlayerDeathEvent(
    val player: PlayerEntity
) : Event()

// ============================================
// Chat Events
// ============================================

/**
 * Fired when a chat message is received.
 * Can be cancelled to prevent display.
 */
class ChatMessageReceivedEvent(
    val message: Text,
    val rawMessage: String
) : CancellableEvent()

/**
 * Fired when the player sends a chat message.
 * Can be cancelled to prevent sending.
 */
class ChatMessageSentEvent(
    val message: String
) : CancellableEvent() {
    /**
     * Modified message to send instead.
     */
    var modifiedMessage: String = message
}

// ============================================
// World Events
// ============================================

/**
 * Fired when the player joins a world/server.
 */
class WorldJoinEvent : Event()

/**
 * Fired when the player leaves a world/server.
 */
class WorldLeaveEvent : Event()

// ============================================
// Game Events
// ============================================

/**
 * Fired when a key is pressed.
 */
class KeyPressEvent(
    val key: Int,
    val scancode: Int,
    val action: Int,
    val modifiers: Int
) : CancellableEvent()

/**
 * Fired when the game is about to render a frame.
 */
class RenderTickEvent(
    val tickDelta: Float
) : Event()
