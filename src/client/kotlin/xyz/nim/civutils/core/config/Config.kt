package xyz.nim.civutils.core.config

import xyz.nim.civutils.core.CivutilsMod
import kotlin.reflect.KClass

/**
 * A configuration value wrapper that tracks changes and supports persistence.
 * Similar to Wynntils' Config<T> class.
 *
 * @param T The type of the config value
 * @param defaultValue The default value for this config
 * @param validator Optional validation function that returns true if the value is valid
 */
class Config<T : Any>(
    val defaultValue: T,
    private val validator: ((T) -> Boolean)? = null
) {
    private var _value: T = defaultValue
    private var _userEdited: Boolean = false

    /**
     * The current value of this config.
     */
    var value: T
        get() = _value
        set(newValue) {
            if (validator != null && !validator.invoke(newValue)) {
                CivutilsMod.logger.warn("Invalid config value rejected: $newValue")
                return
            }
            if (_value != newValue) {
                _value = newValue
                _userEdited = true
                onChanged?.invoke(newValue)
                CivutilsMod.configManager.markDirty()
            }
        }

    /**
     * Whether this config has been edited by the user (differs from default).
     */
    val userEdited: Boolean get() = _userEdited

    /**
     * Callback invoked when the value changes.
     */
    var onChanged: ((T) -> Unit)? = null

    /**
     * The owner of this config (Feature, Overlay, etc.)
     */
    internal var owner: Any? = null

    /**
     * The field name of this config in its owner.
     */
    internal var fieldName: String = ""

    /**
     * Check if the current value differs from the default.
     */
    fun isModified(): Boolean = _value != defaultValue

    /**
     * Reset this config to its default value.
     */
    fun reset() {
        _value = defaultValue
        _userEdited = false
        onChanged?.invoke(_value)
    }

    /**
     * Set the value without triggering the changed callback.
     * Used during config loading.
     */
    internal fun setValueSilently(newValue: T, markEdited: Boolean = false) {
        _value = newValue
        _userEdited = markEdited
    }

    /**
     * Get the type of the config value.
     */
    @Suppress("UNCHECKED_CAST")
    fun getValueType(): KClass<T> = defaultValue::class as KClass<T>

    /**
     * Try to parse a string value into the config type.
     * Useful for GUI input fields.
     */
    @Suppress("UNCHECKED_CAST")
    fun tryParseString(stringValue: String): T? {
        return try {
            when (defaultValue) {
                is String -> stringValue as T
                is Int -> stringValue.toIntOrNull() as? T
                is Long -> stringValue.toLongOrNull() as? T
                is Float -> stringValue.toFloatOrNull() as? T
                is Double -> stringValue.toDoubleOrNull() as? T
                is Boolean -> stringValue.toBooleanStrictOrNull() as? T
                is Enum<*> -> {
                    val enumClass = defaultValue::class.java
                    enumClass.enumConstants?.find {
                        (it as Enum<*>).name.equals(stringValue, ignoreCase = true)
                    } as? T
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Get valid literals for enum types.
     */
    fun getValidLiterals(): List<String>? {
        return when (defaultValue) {
            is Boolean -> listOf("true", "false")
            is Enum<*> -> defaultValue::class.java.enumConstants?.map { (it as Enum<*>).name }
            else -> null
        }
    }

    override fun toString(): String = "Config($fieldName=$_value, default=$defaultValue, edited=$_userEdited)"
}

/**
 * Convenience function to create a config with a range validator.
 */
fun intConfig(default: Int, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE): Config<Int> {
    return Config(default) { it in min..max }
}

/**
 * Convenience function to create a config with a range validator.
 */
fun floatConfig(default: Float, min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE): Config<Float> {
    return Config(default) { it in min..max }
}
