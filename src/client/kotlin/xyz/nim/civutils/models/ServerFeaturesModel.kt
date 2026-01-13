package xyz.nim.civutils.models

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.model.Model

/**
 * Represents a server feature with enabled status and optional configuration.
 */
data class ServerFeature(
    val enabled: Boolean,
    val config: JsonObject? = null
) {
    /**
     * Get a config value as a specific type.
     * Returns null if the config doesn't exist or can't be converted.
     */
    inline fun <reified T> getConfig(key: String): T? {
        val element = config?.get(key) ?: return null
        return try {
            Gson().fromJson(element, T::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get a config value as a string.
     */
    fun getConfigString(key: String): String? = config?.get(key)?.asString

    /**
     * Get a config value as an integer.
     */
    fun getConfigInt(key: String): Int? = config?.get(key)?.asInt

    /**
     * Get a config value as a double.
     */
    fun getConfigDouble(key: String): Double? = config?.get(key)?.asDouble

    /**
     * Get a config value as a boolean.
     */
    fun getConfigBoolean(key: String): Boolean? = config?.get(key)?.asBoolean

    /**
     * Get a config value as a list of strings.
     */
    fun getConfigStringList(key: String): List<String>? {
        val array = config?.getAsJsonArray(key) ?: return null
        return array.map { it.asString }
    }
}

/**
 * Model that stores server feature availability and configuration.
 *
 * Populated via the civ:handshake plugin channel response.
 * Used by overlays and features to check if they should be available.
 */
object ServerFeaturesModel : Model() {

    /** Server name from handshake */
    var serverName: String = ""
        private set

    /** Server version from handshake */
    var serverVersion: String = ""
        private set

    /** Protocol version from handshake */
    var protocolVersion: Int = 0
        private set

    /** Channels the server supports */
    var supportedChannels: List<String> = emptyList()
        private set

    /** Map of feature name to feature data */
    private val features = mutableMapOf<String, ServerFeature>()

    /** Whether we've received a handshake response */
    var handshakeReceived: Boolean = false
        private set

    /** Timestamp of last handshake */
    var lastHandshakeTime: Long = 0
        private set

    override fun reset() {
        serverName = ""
        serverVersion = ""
        protocolVersion = 0
        supportedChannels = emptyList()
        features.clear()
        handshakeReceived = false
        lastHandshakeTime = 0
        CivutilsMod.logger.debug("ServerFeaturesModel: Reset")
    }

    /**
     * Update from handshake response.
     */
    fun updateFromHandshake(
        serverName: String,
        serverVersion: String,
        protocolVersion: Int,
        channels: List<String>,
        featuresData: Map<String, ServerFeature>
    ) {
        this.serverName = serverName
        this.serverVersion = serverVersion
        this.protocolVersion = protocolVersion
        this.supportedChannels = channels
        this.features.clear()
        this.features.putAll(featuresData)
        this.handshakeReceived = true
        this.lastHandshakeTime = System.currentTimeMillis()

        CivutilsMod.logger.info(
            "ServerFeaturesModel: Handshake received from '$serverName' v$serverVersion, " +
            "protocol=$protocolVersion, ${features.size} features, ${channels.size} channels"
        )

        // Log feature details
        features.forEach { (name, feature) ->
            CivutilsMod.logger.debug("  Feature '$name': enabled=${feature.enabled}")
        }
    }

    /**
     * Check if a feature is enabled on the server.
     * Returns false if handshake not received or feature unknown.
     */
    fun isFeatureEnabled(featureName: String): Boolean {
        if (!handshakeReceived) return false
        return features[featureName]?.enabled ?: false
    }

    /**
     * Get a feature by name.
     * Returns null if handshake not received or feature unknown.
     */
    fun getFeature(featureName: String): ServerFeature? {
        if (!handshakeReceived) return null
        return features[featureName]
    }

    /**
     * Get a config value from a feature.
     * Returns null if feature doesn't exist or config key not found.
     */
    inline fun <reified T> getFeatureConfig(featureName: String, configKey: String): T? {
        return getFeature(featureName)?.getConfig<T>(configKey)
    }

    /**
     * Check if a specific channel is supported by the server.
     */
    fun isChannelSupported(channelId: String): Boolean {
        return channelId in supportedChannels
    }

    /**
     * Get all feature names.
     */
    fun getFeatureNames(): Set<String> = features.keys

    /**
     * Get all enabled features.
     */
    fun getEnabledFeatures(): Map<String, ServerFeature> {
        return features.filter { it.value.enabled }
    }

    // === Convenience methods for common features ===

    /**
     * Get available class names from the classes feature.
     * Returns empty list if classes feature not enabled.
     */
    fun getAvailableClasses(): List<String> {
        val classesFeature = getFeature("classes") ?: return emptyList()
        if (!classesFeature.enabled) return emptyList()
        return classesFeature.getConfigStringList("classes") ?: emptyList()
    }

    /**
     * Get max class level from the classes feature.
     * Returns default (5) if not specified.
     */
    fun getMaxClassLevel(): Int {
        return getFeature("classes")?.getConfigInt("maxLevel") ?: 5
    }

    /**
     * Get combat timer duration in seconds.
     * Returns default (30) if not specified.
     */
    fun getCombatTimerDuration(): Int {
        return getFeature("combat")?.getConfigInt("timerDuration") ?: 30
    }
}
