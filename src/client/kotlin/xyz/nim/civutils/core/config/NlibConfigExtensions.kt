package xyz.nim.civutils.core.config

import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.*

/**
 * Kotlin extensions for nlib config options.
 * Makes using nlib configs feel more natural in Kotlin.
 */

// === Property-style access ===

/** Get the value using property syntax: config.value */
var <T> ConfigOption<T>.value: T
    get() = getValue()
    set(newValue) = setValue(newValue)

// === Convenience factory functions ===

/** Create a boolean config */
fun booleanConfig(
    name: String,
    default: Boolean,
    displayName: String? = null,
    comment: String? = null
): BooleanConfig {
    val builder = BooleanConfig.create(name, default)
    displayName?.let { builder.displayName(it) }
    comment?.let { builder.comment(it) }
    return builder.build()
}

/** Create an integer config with optional range */
fun intConfig(
    name: String,
    default: Int,
    min: Int = Int.MIN_VALUE,
    max: Int = Int.MAX_VALUE,
    displayName: String? = null,
    comment: String? = null
): IntegerConfig {
    val builder = IntegerConfig.create(name, default)
    if (min != Int.MIN_VALUE || max != Int.MAX_VALUE) {
        builder.range(min, max)
    }
    builder.rejectInvalid() // Use rejection mode like old civutils
    displayName?.let { builder.displayName(it) }
    comment?.let { builder.comment(it) }
    return builder.build()
}

/** Create a float config with optional range */
fun floatConfig(
    name: String,
    default: Float,
    min: Float = -Float.MAX_VALUE,
    max: Float = Float.MAX_VALUE,
    displayName: String? = null,
    comment: String? = null
): FloatConfig {
    val builder = FloatConfig.create(name, default)
    if (min != -Float.MAX_VALUE || max != Float.MAX_VALUE) {
        builder.range(min, max)
    }
    builder.rejectInvalid() // Use rejection mode like old civutils
    displayName?.let { builder.displayName(it) }
    comment?.let { builder.comment(it) }
    return builder.build()
}

/** Create a double config with optional range */
fun doubleConfig(
    name: String,
    default: Double,
    min: Double = -Double.MAX_VALUE,
    max: Double = Double.MAX_VALUE,
    displayName: String? = null,
    comment: String? = null
): DoubleConfig {
    val builder = DoubleConfig.create(name, default)
    if (min != -Double.MAX_VALUE || max != Double.MAX_VALUE) {
        builder.range(min, max)
    }
    builder.rejectInvalid()
    displayName?.let { builder.displayName(it) }
    comment?.let { builder.comment(it) }
    return builder.build()
}

/** Create a string config */
fun stringConfig(
    name: String,
    default: String,
    maxLength: Int = -1,
    displayName: String? = null,
    comment: String? = null
): StringConfig {
    val builder = StringConfig.create(name, default)
    if (maxLength > 0) {
        builder.maxLength(maxLength)
    }
    displayName?.let { builder.displayName(it) }
    comment?.let { builder.comment(it) }
    return builder.build()
}

/** Create a color config (ARGB int) */
fun colorConfig(
    name: String,
    default: Int,
    includeAlpha: Boolean = true,
    displayName: String? = null,
    comment: String? = null
): ColorConfig {
    val builder = ColorConfig.create(name, default)
    builder.includeAlpha(includeAlpha)
    displayName?.let { builder.displayName(it) }
    comment?.let { builder.comment(it) }
    return builder.build()
}

/** Create an enum config */
inline fun <reified E : Enum<E>> enumConfig(
    name: String,
    default: E,
    displayName: String? = null,
    comment: String? = null
): OptionListConfig<E> {
    val builder = OptionListConfig.create(name, default, E::class.java)
    displayName?.let { builder.displayName(it) }
    comment?.let { builder.comment(it) }
    return builder.build()
}

// === Toggle for boolean ===

/** Toggle a boolean config */
fun BooleanConfig.toggle() {
    value = !value
}

// === Cycle for enum ===

/** Cycle to next enum value */
fun <E : Enum<E>> OptionListConfig<E>.next() = cycleNext()

/** Cycle to previous enum value */
fun <E : Enum<E>> OptionListConfig<E>.previous() = cyclePrevious()

// === Callback extensions ===

/** Add a value change callback (Kotlin lambda) - returns the config for chaining */
fun <T : ConfigOption<V>, V> T.onChange(callback: (V) -> Unit): T {
    this.onValueChanged { callback(it) }
    return this
}

/** Add a load callback (Kotlin lambda) - returns the config for chaining */
fun <T : ConfigOption<V>, V> T.onLoaded(callback: (V) -> Unit): T {
    this.onLoad { callback(it) }
    return this
}
