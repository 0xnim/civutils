package xyz.nim.civutils.core.config

/**
 * Marks a Config field to be automatically persisted to disk.
 * The config will be saved when changed and loaded on startup.
 *
 * Usage:
 * ```
 * @Persisted
 * val myConfig = Config(defaultValue = 100)
 * ```
 */
@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.RUNTIME)
annotation class Persisted(
    /**
     * Optional custom key for the config in the JSON file.
     * If not specified, the field name is used.
     */
    val key: String = "",

    /**
     * Optional i18n key for the display name.
     * If not specified, auto-generated from the config path.
     */
    val i18nKey: String = ""
)
