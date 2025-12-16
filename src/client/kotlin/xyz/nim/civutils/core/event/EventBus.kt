package xyz.nim.civutils.core.event

import xyz.nim.civutils.core.CivutilsMod
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberFunctions
import kotlin.reflect.jvm.isAccessible

/**
 * Marks a function as an event handler.
 * The function must have exactly one parameter: the event type.
 *
 * Usage:
 * ```
 * @Subscribe
 * fun onPlayerDamage(event: PlayerDamageEvent) {
 *     // handle event
 * }
 * ```
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Subscribe(
    /**
     * Priority of this handler. Higher values run first.
     */
    val priority: Int = 0,

    /**
     * If true, this handler will receive cancelled events.
     */
    val receiveCancelled: Boolean = false
)

/**
 * Base class for all events.
 */
abstract class Event {
    /**
     * Whether this event has been cancelled.
     * Cancelled events won't be processed by handlers (unless receiveCancelled = true).
     */
    var cancelled: Boolean = false
        private set

    /**
     * Cancel this event, preventing further processing.
     */
    fun cancel() {
        cancelled = true
    }
}

/**
 * An event that can be cancelled to prevent the default action.
 */
abstract class CancellableEvent : Event()

/**
 * Simple event bus for publishing and subscribing to events.
 * Supports priority ordering and cancellation.
 */
class EventBus {
    private data class Handler(
        val owner: Any,
        val function: kotlin.reflect.KFunction<*>,
        val priority: Int,
        val receiveCancelled: Boolean
    )

    private val handlers = ConcurrentHashMap<KClass<*>, MutableList<Handler>>()
    private val registeredOwners = ConcurrentHashMap.newKeySet<Any>()

    /**
     * Register an object to receive events.
     * All functions with @Subscribe annotation will be registered.
     */
    fun register(owner: Any) {
        if (!registeredOwners.add(owner)) {
            CivutilsMod.logger.warn("Object already registered to event bus: ${owner::class.simpleName}")
            return
        }

        val kClass = owner::class
        var count = 0

        for (func in kClass.memberFunctions) {
            val annotation = func.findAnnotation<Subscribe>() ?: continue
            func.isAccessible = true

            // Validate function signature: must have exactly one parameter (plus receiver)
            val params = func.parameters
            if (params.size != 2) {
                CivutilsMod.logger.warn(
                    "Invalid @Subscribe function ${kClass.simpleName}::${func.name}: " +
                    "must have exactly one parameter (the event)"
                )
                continue
            }

            val eventType = params[1].type.classifier as? KClass<*>
            if (eventType == null || !Event::class.java.isAssignableFrom(eventType.java)) {
                CivutilsMod.logger.warn(
                    "Invalid @Subscribe function ${kClass.simpleName}::${func.name}: " +
                    "parameter must be an Event subclass"
                )
                continue
            }

            val handler = Handler(
                owner = owner,
                function = func,
                priority = annotation.priority,
                receiveCancelled = annotation.receiveCancelled
            )

            handlers.computeIfAbsent(eventType) { mutableListOf() }.apply {
                add(handler)
                sortByDescending { it.priority }
            }

            count++
        }

        if (count > 0) {
            CivutilsMod.logger.debug("Registered $count event handlers from ${kClass.simpleName}")
        }
    }

    /**
     * Unregister an object from receiving events.
     */
    fun unregister(owner: Any) {
        if (!registeredOwners.remove(owner)) return

        for ((_, handlerList) in handlers) {
            handlerList.removeIf { it.owner === owner }
        }

        CivutilsMod.logger.debug("Unregistered ${owner::class.simpleName} from event bus")
    }

    /**
     * Post an event to all registered handlers.
     * Returns the event (possibly modified by handlers).
     */
    fun <T : Event> post(event: T): T {
        val eventType = event::class
        val handlerList = handlers[eventType] ?: return event

        for (handler in handlerList) {
            if (event.cancelled && !handler.receiveCancelled) continue

            try {
                handler.function.call(handler.owner, event)
            } catch (e: Exception) {
                CivutilsMod.logger.error(
                    "Error in event handler ${handler.owner::class.simpleName}::${handler.function.name}",
                    e
                )
            }
        }

        return event
    }

    /**
     * Post an event and return whether it was cancelled.
     */
    fun <T : Event> postAndCheckCancelled(event: T): Boolean {
        post(event)
        return event.cancelled
    }

    /**
     * Get the number of handlers for an event type.
     */
    fun getHandlerCount(eventType: KClass<*>): Int {
        return handlers[eventType]?.size ?: 0
    }
}
