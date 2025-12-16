package xyz.nim.civutils.gui.widgets

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.DrawContext
import xyz.nim.civutils.gui.theme.CivutilsTheme

/**
 * Manages toast notifications that appear at the bottom-right of the screen.
 * Toasts automatically fade out after a duration.
 */
class ToastManager {

    companion object {
        private const val TOAST_DURATION_MS = 3000L
        private const val FADE_DURATION_MS = 500L
        private const val TOAST_HEIGHT = 24
        private const val TOAST_PADDING = 8
        private const val TOAST_MARGIN = 4
        private const val MAX_TOASTS = 3
    }

    enum class ToastType(val color: Int, val icon: String) {
        SUCCESS(CivutilsTheme.TOAST_SUCCESS, "\u2713"),  // ✓
        ERROR(CivutilsTheme.TOAST_ERROR, "\u2717"),      // ✗
        INFO(CivutilsTheme.TOAST_INFO, "i"),
        WARNING(CivutilsTheme.TOAST_WARNING, "!")
    }

    private data class Toast(
        val message: String,
        val type: ToastType,
        val createdAt: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean =
            System.currentTimeMillis() - createdAt > TOAST_DURATION_MS + FADE_DURATION_MS

        fun getOpacity(): Float {
            val age = System.currentTimeMillis() - createdAt
            return when {
                age < TOAST_DURATION_MS -> 1.0f
                else -> {
                    val fadeProgress = (age - TOAST_DURATION_MS) / FADE_DURATION_MS.toFloat()
                    maxOf(0f, 1f - fadeProgress)
                }
            }
        }
    }

    private val toasts = mutableListOf<Toast>()

    fun success(message: String) = addToast(message, ToastType.SUCCESS)
    fun error(message: String) = addToast(message, ToastType.ERROR)
    fun info(message: String) = addToast(message, ToastType.INFO)
    fun warning(message: String) = addToast(message, ToastType.WARNING)

    private fun addToast(message: String, type: ToastType) {
        if (toasts.size >= MAX_TOASTS) {
            toasts.removeAt(0)
        }
        toasts.add(Toast(message, type))
    }

    fun render(context: DrawContext, textRenderer: TextRenderer, screenWidth: Int, screenHeight: Int) {
        // Remove expired toasts
        toasts.removeAll { it.isExpired() }

        var y = screenHeight - TOAST_MARGIN - TOAST_HEIGHT
        for (i in toasts.indices.reversed()) {
            val toast = toasts[i]
            val opacity = toast.getOpacity()
            if (opacity <= 0) continue

            val textWidth = textRenderer.getWidth(toast.message)
            val toastWidth = textWidth + TOAST_PADDING * 2 + 16
            val x = screenWidth - toastWidth - TOAST_MARGIN

            // Calculate colors with opacity
            val alpha = (opacity * 230).toInt()
            val bgColor = (alpha shl 24) or (toast.type.color and 0x00FFFFFF)
            val textAlpha = (opacity * 255).toInt()
            val textColor = (textAlpha shl 24) or 0x00FFFFFF

            // Draw background
            context.fill(x, y, x + toastWidth, y + TOAST_HEIGHT, bgColor)

            // Draw icon and message
            context.drawText(textRenderer, toast.type.icon, x + TOAST_PADDING, y + (TOAST_HEIGHT - 8) / 2, textColor, false)
            context.drawText(textRenderer, toast.message, x + TOAST_PADDING + 14, y + (TOAST_HEIGHT - 8) / 2, textColor, false)

            y -= TOAST_HEIGHT + TOAST_MARGIN
        }
    }

    fun hasToasts(): Boolean = toasts.isNotEmpty()

    fun clear() = toasts.clear()
}
