package xyz.nim.civutils.gui.theme

import kotlin.math.min

/**
 * Centralized theme system for CivUtils UI.
 * Provides colors, responsive breakpoints, and sizing functions.
 */
object CivutilsTheme {

    // === PANEL COLORS ===
    const val PANEL_BG = 0xA0000000.toInt()
    const val PANEL_BORDER = 0xFFC0C0C0.toInt()
    const val HEADER_BG = 0xFF222222.toInt()

    // === TEXT COLORS ===
    const val TEXT_PRIMARY = 0xFFFFFFFF.toInt()
    const val TEXT_SECONDARY = 0xFFAAAAAA.toInt()
    const val TEXT_MUTED = 0xFF888888.toInt()

    // === STATUS COLORS ===
    const val SUCCESS = 0xFF55FF55.toInt()
    const val ERROR = 0xFFFF5555.toInt()
    const val WARNING = 0xFFFFFF55.toInt()
    const val INFO = 0xFF55FFFF.toInt()

    // === INTERACTIVE COLORS ===
    const val SELECTED = 0xFFFFFF00.toInt()
    const val HOVER = 0x40FFFFFF
    const val ACCENT = 0xFF5555FF.toInt()
    const val ACCENT_HOVER = 0xFF7777FF.toInt()

    // === BACKGROUND COLORS ===
    const val BACKGROUND = 0xCC000000.toInt()
    const val BACKGROUND_LIGHT = 0xCC1A1A1A.toInt()
    const val BACKGROUND_HOVER = 0xCC2A2A2A.toInt()

    // === STATE COLORS ===
    const val ENABLED = 0xFF00AA00.toInt()
    const val DISABLED = 0xFFAA0000.toInt()

    // === TOAST COLORS ===
    const val TOAST_SUCCESS = 0xFF2E7D32.toInt()
    const val TOAST_ERROR = 0xFFC62828.toInt()
    const val TOAST_INFO = 0xFF1565C0.toInt()
    const val TOAST_WARNING = 0xFFF57C00.toInt()

    // === RESPONSIVE BREAKPOINTS ===
    enum class ScreenSize {
        SMALL,
        MEDIUM,
        LARGE,
        XLARGE
    }

    fun getScreenSize(width: Int, height: Int): ScreenSize {
        val minDimension = min(width, height)
        return when {
            minDimension < 300 -> ScreenSize.SMALL
            minDimension < 450 -> ScreenSize.MEDIUM
            minDimension < 600 -> ScreenSize.LARGE
            else -> ScreenSize.XLARGE
        }
    }

    // === RESPONSIVE SPACING ===
    fun margin(size: ScreenSize): Int = when (size) {
        ScreenSize.SMALL -> 6
        ScreenSize.MEDIUM -> 10
        ScreenSize.LARGE -> 15
        ScreenSize.XLARGE -> 20
    }

    fun padding(size: ScreenSize): Int = when (size) {
        ScreenSize.SMALL -> 4
        ScreenSize.MEDIUM -> 6
        ScreenSize.LARGE -> 8
        ScreenSize.XLARGE -> 10
    }

    fun spacing(size: ScreenSize): Int = when (size) {
        ScreenSize.SMALL -> 4
        ScreenSize.MEDIUM -> 6
        ScreenSize.LARGE -> 8
        ScreenSize.XLARGE -> 10
    }

    // === RESPONSIVE SIZES ===
    fun buttonHeight(size: ScreenSize): Int = when (size) {
        ScreenSize.SMALL -> 16
        ScreenSize.MEDIUM -> 18
        ScreenSize.LARGE -> 20
        ScreenSize.XLARGE -> 22
    }

    fun headerHeight(size: ScreenSize): Int = when (size) {
        ScreenSize.SMALL -> 20
        ScreenSize.MEDIUM -> 24
        ScreenSize.LARGE -> 28
        ScreenSize.XLARGE -> 32
    }

    fun controlHeight(size: ScreenSize): Int = when (size) {
        ScreenSize.SMALL -> 16
        ScreenSize.MEDIUM -> 18
        ScreenSize.LARGE -> 20
        ScreenSize.XLARGE -> 20
    }

    fun buttonWidth(size: ScreenSize): Int = when (size) {
        ScreenSize.SMALL -> 60
        ScreenSize.MEDIUM -> 80
        ScreenSize.LARGE -> 100
        ScreenSize.XLARGE -> 120
    }

    fun smallButtonWidth(size: ScreenSize): Int = when (size) {
        ScreenSize.SMALL -> 40
        ScreenSize.MEDIUM -> 50
        ScreenSize.LARGE -> 60
        ScreenSize.XLARGE -> 70
    }
}
