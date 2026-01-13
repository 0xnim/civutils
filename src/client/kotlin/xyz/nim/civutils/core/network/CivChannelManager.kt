package xyz.nim.civutils.core.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.event.ClassXpChannelEvent
import xyz.nim.civutils.core.event.CivHandshakeEvent
import xyz.nim.civutils.core.event.Subscribe
import xyz.nim.civutils.core.event.WorldJoinEvent
import xyz.nim.civutils.core.network.payloads.CivHandshakePayload
import xyz.nim.civutils.core.network.payloads.ClassXpPayload
import xyz.nim.civutils.models.ServerFeature
import xyz.nim.civutils.models.ServerFeaturesModel

/**
 * Manages CivUtils plugin channel communication.
 *
 * Handles:
 * - Channel registration with Fabric networking
 * - Sending handshake on world join
 * - Parsing incoming messages and firing events
 */
object CivChannelManager {

    private val gson = Gson()

    /** Current protocol version */
    const val PROTOCOL_VERSION = 1

    /** Client version */
    const val CLIENT_VERSION = "1.0.0"

    /** Channels this client supports */
    val SUPPORTED_CHANNELS = listOf("civ:class_xp")

    private var initialized = false

    /**
     * Initialize the channel manager.
     * Call once during mod initialization.
     */
    fun initialize() {
        if (initialized) return

        CivutilsMod.logger.info("CivChannelManager: Initializing plugin channels")

        // Register payload types
        registerPayloadTypes()

        // Register receivers
        registerReceivers()

        // Register for events
        CivutilsMod.eventBus.register(this)

        initialized = true
        CivutilsMod.logger.info("CivChannelManager: Initialized")
    }

    /**
     * Register payload types with Fabric.
     */
    private fun registerPayloadTypes() {
        // Register for S2C (server to client)
        PayloadTypeRegistry.playS2C().register(CivHandshakePayload.TYPE, CivHandshakePayload.CODEC)
        PayloadTypeRegistry.playS2C().register(ClassXpPayload.TYPE, ClassXpPayload.CODEC)

        // Register for C2S (client to server)
        PayloadTypeRegistry.playC2S().register(CivHandshakePayload.TYPE, CivHandshakePayload.CODEC)

        CivutilsMod.logger.debug("CivChannelManager: Payload types registered")
    }

    /**
     * Register packet receivers.
     */
    private fun registerReceivers() {
        // Handshake response from server
        ClientPlayNetworking.registerGlobalReceiver(CivHandshakePayload.TYPE) { payload, context ->
            handleHandshakeResponse(payload.jsonData)
        }

        // Class XP updates from server
        ClientPlayNetworking.registerGlobalReceiver(ClassXpPayload.TYPE) { payload, context ->
            handleClassXpMessage(payload.jsonData)
        }

        CivutilsMod.logger.debug("CivChannelManager: Receivers registered")
    }

    /**
     * Send handshake to server on world join.
     */
    @Subscribe
    fun onWorldJoin(event: WorldJoinEvent) {
        sendHandshake()
    }

    /**
     * Send handshake message to the server.
     */
    fun sendHandshake() {
        if (!ClientPlayNetworking.canSend(CivHandshakePayload.TYPE)) {
            CivutilsMod.logger.debug("CivChannelManager: Server doesn't accept handshake channel")
            return
        }

        val handshakeData = JsonObject().apply {
            addProperty("client", "civutils")
            addProperty("version", CLIENT_VERSION)
            addProperty("protocol", PROTOCOL_VERSION)
            add("channels", gson.toJsonTree(SUPPORTED_CHANNELS))
        }

        val payload = CivHandshakePayload(handshakeData.toString())
        ClientPlayNetworking.send(payload)

        CivutilsMod.logger.info("CivChannelManager: Sent handshake to server")
    }

    /**
     * Handle handshake response from server.
     */
    private fun handleHandshakeResponse(jsonData: String) {
        try {
            val json = JsonParser.parseString(jsonData).asJsonObject

            val serverName = json.get("server")?.asString ?: "Unknown"
            val serverVersion = json.get("version")?.asString ?: "Unknown"
            val protocolVersion = json.get("protocol")?.asInt ?: 0
            val channels = json.getAsJsonArray("channels")?.map { it.asString } ?: emptyList()

            // Parse features
            val featuresJson = json.getAsJsonObject("features")
            val features = mutableMapOf<String, ServerFeature>()

            featuresJson?.entrySet()?.forEach { (name, element) ->
                val featureObj = element.asJsonObject
                val enabled = featureObj.get("enabled")?.asBoolean ?: false
                val config = featureObj.getAsJsonObject("config")
                features[name] = ServerFeature(enabled, config)
            }

            // Update ServerFeaturesModel
            ServerFeaturesModel.updateFromHandshake(
                serverName = serverName,
                serverVersion = serverVersion,
                protocolVersion = protocolVersion,
                channels = channels,
                featuresData = features
            )

            // Fire event
            CivutilsMod.eventBus.post(CivHandshakeEvent(
                serverName = serverName,
                serverVersion = serverVersion,
                supportedChannels = channels,
                features = features
            ))

            CivutilsMod.logger.info(
                "CivChannelManager: Handshake response from '$serverName' - " +
                "${features.size} features, ${channels.size} channels"
            )

        } catch (e: Exception) {
            CivutilsMod.logger.error("CivChannelManager: Failed to parse handshake response", e)
        }
    }

    /**
     * Handle class XP message from server.
     */
    private fun handleClassXpMessage(jsonData: String) {
        try {
            val json = JsonParser.parseString(jsonData).asJsonObject
            val type = json.get("type")?.asString ?: return

            CivutilsMod.logger.debug("CivChannelManager: Received class_xp message type=$type")

            when (type) {
                "full" -> handleFullClassData(json)
                "partial" -> handlePartialClassData(json)
                "levelup", "leveldown" -> handleLevelChange(json, type)
                else -> CivutilsMod.logger.warn("CivChannelManager: Unknown class_xp message type: $type")
            }

        } catch (e: Exception) {
            CivutilsMod.logger.error("CivChannelManager: Failed to parse class_xp message", e)
        }
    }

    /**
     * Handle full class data message.
     */
    private fun handleFullClassData(json: JsonObject) {
        val currentClass = json.get("currentClass")?.asString
        val classesJson = json.getAsJsonObject("classes") ?: return

        val classData = mutableMapOf<String, ClassChannelData>()
        classesJson.entrySet().forEach { (name, element) ->
            val obj = element.asJsonObject
            classData[name] = ClassChannelData(
                level = obj.get("level")?.asInt ?: 0,
                levelName = obj.get("levelName")?.asString,
                totalXp = obj.get("totalXp")?.asDouble ?: 0.0,
                currentXp = obj.get("currentXp")?.asInt ?: 0,
                xpForLevel = obj.get("xpForLevel")?.asInt ?: 0,
                change = null
            )
        }

        CivutilsMod.eventBus.post(ClassXpChannelEvent(
            type = "full",
            classes = classData,
            singleClass = null,
            currentClass = currentClass
        ))
    }

    /**
     * Handle partial class data message.
     */
    private fun handlePartialClassData(json: JsonObject) {
        val className = json.get("class")?.asString ?: return
        val totalXp = json.get("totalXp")?.asDouble ?: return
        val change = json.get("change")?.asDouble ?: 0.0
        val currentClass = json.get("currentClass")?.asString

        val classData = mapOf(className to ClassChannelData(
            level = null,
            levelName = null,
            totalXp = totalXp,
            currentXp = null,
            xpForLevel = null,
            change = change
        ))

        CivutilsMod.eventBus.post(ClassXpChannelEvent(
            type = "partial",
            classes = classData,
            singleClass = className,
            currentClass = currentClass
        ))
    }

    /**
     * Handle level change message.
     */
    private fun handleLevelChange(json: JsonObject, type: String) {
        val className = json.get("class")?.asString ?: return
        val level = json.get("level")?.asInt ?: return
        val levelName = json.get("levelName")?.asString
        val totalXp = json.get("totalXp")?.asDouble ?: 0.0

        val classData = mapOf(className to ClassChannelData(
            level = level,
            levelName = levelName,
            totalXp = totalXp,
            currentXp = null,
            xpForLevel = null,
            change = null
        ))

        CivutilsMod.eventBus.post(ClassXpChannelEvent(
            type = type,
            classes = classData,
            singleClass = className,
            currentClass = null
        ))
    }
}

/**
 * Data received from class XP channel.
 * Nullable fields indicate the field wasn't included in this message type.
 */
data class ClassChannelData(
    val level: Int?,
    val levelName: String?,
    val totalXp: Double,
    val currentXp: Int?,
    val xpForLevel: Int?,
    val change: Double?
)
