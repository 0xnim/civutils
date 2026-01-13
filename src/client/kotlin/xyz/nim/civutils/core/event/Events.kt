package xyz.nim.civutils.core.event

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player

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
    val guiGraphics: GuiGraphics,
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
    val player: Player,
    val amount: Float,
    val newHealth: Float
) : Event()

/**
 * Fired when the player's health changes.
 */
class PlayerHealthChangeEvent(
    val player: Player,
    val oldHealth: Float,
    val newHealth: Float
) : CancellableEvent()

/**
 * Fired when the player dies.
 */
class PlayerDeathEvent(
    val player: Player
) : Event()

// ============================================
// Chat Events
// ============================================

/**
 * Fired when a chat message is received.
 * Can be cancelled to prevent display.
 */
class ChatMessageReceivedEvent(
    val message: Component,
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

// ============================================
// ActionBar Events
// ============================================

/**
 * Fired when an actionbar message is received.
 * Can be cancelled to prevent display.
 */
class ActionBarMessageEvent(
    val message: Component,
    val rawMessage: String
) : CancellableEvent()

// ============================================
// BossBar Events
// ============================================

/**
 * Fired when a BossBar is added, updated, or removed.
 */
class BossBarEvent(
    val uuid: java.util.UUID,
    val name: String,
    val progress: Float,
    val color: net.minecraft.world.BossEvent.BossBarColor,
    val overlay: net.minecraft.world.BossEvent.BossBarOverlay,
    val action: BossBarAction
) : Event()

enum class BossBarAction {
    ADD,
    UPDATE,
    REMOVE
}

// ============================================
// Screen/Container Events
// ============================================

/**
 * Fired when a screen is opened.
 */
class ScreenOpenEvent(
    val screen: net.minecraft.client.gui.screens.Screen
) : CancellableEvent()

/**
 * Fired when a container screen's contents are updated.
 */
class ContainerUpdateEvent(
    val menu: net.minecraft.world.inventory.AbstractContainerMenu,
    val slot: Int,
    val stack: net.minecraft.world.item.ItemStack
) : Event()

// ============================================
// Plugin Channel Events
// ============================================

/**
 * Fired when a handshake response is received from the server.
 * Contains server capabilities and feature configuration.
 */
class CivHandshakeEvent(
    val serverName: String,
    val serverVersion: String,
    val supportedChannels: List<String>,
    val features: Map<String, xyz.nim.civutils.models.ServerFeature>
) : Event()

/**
 * Fired when class XP data is received via plugin channel.
 */
class ClassXpChannelEvent(
    /** Message type: "full", "partial", "levelup", "leveldown" */
    val type: String,
    /** Class data (all classes for "full", single class for others) */
    val classes: Map<String, xyz.nim.civutils.core.network.ClassChannelData>?,
    /** The single class being updated (for partial/levelup/leveldown) */
    val singleClass: String?,
    /** Currently active class (may be null) */
    val currentClass: String?
) : Event()
