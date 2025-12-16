package xyz.nim.civutils.gui.widgets

import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import xyz.nim.civutils.gui.theme.ResponsiveLayout

/**
 * Factory functions for creating themed buttons.
 */
object Buttons {

    /**
     * Create a standard button with responsive sizing.
     */
    fun create(
        label: Text,
        x: Int,
        y: Int,
        layout: ResponsiveLayout,
        action: ButtonWidget.PressAction
    ): ButtonWidget {
        return ButtonWidget.builder(label, action)
            .dimensions(x, y, layout.buttonWidth, layout.buttonHeight)
            .build()
    }

    /**
     * Create a button with custom width.
     */
    fun create(
        label: Text,
        x: Int,
        y: Int,
        width: Int,
        layout: ResponsiveLayout,
        action: ButtonWidget.PressAction
    ): ButtonWidget {
        return ButtonWidget.builder(label, action)
            .dimensions(x, y, width, layout.buttonHeight)
            .build()
    }

    /**
     * Create a button with custom dimensions.
     */
    fun create(
        label: Text,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        action: ButtonWidget.PressAction
    ): ButtonWidget {
        return ButtonWidget.builder(label, action)
            .dimensions(x, y, width, height)
            .build()
    }

    /**
     * Create a small (icon-sized) square button.
     */
    fun small(
        label: Text,
        x: Int,
        y: Int,
        layout: ResponsiveLayout,
        action: ButtonWidget.PressAction
    ): ButtonWidget {
        val size = layout.buttonHeight
        return ButtonWidget.builder(label, action)
            .dimensions(x, y, size, size)
            .build()
    }

    /**
     * Create a small button with custom width.
     */
    fun small(
        label: Text,
        x: Int,
        y: Int,
        width: Int,
        layout: ResponsiveLayout,
        action: ButtonWidget.PressAction
    ): ButtonWidget {
        return ButtonWidget.builder(label, action)
            .dimensions(x, y, width, layout.buttonHeight)
            .build()
    }

    /**
     * Create a toggle/tab button with active state.
     */
    fun toggle(
        label: Text,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        active: Boolean,
        action: ButtonWidget.PressAction
    ): ButtonWidget {
        return ButtonWidget.builder(label, action)
            .dimensions(x, y, width, height)
            .build().also { it.active = active }
    }

    /**
     * Create a toggle button with responsive height.
     */
    fun toggle(
        label: Text,
        x: Int,
        y: Int,
        width: Int,
        layout: ResponsiveLayout,
        active: Boolean,
        action: ButtonWidget.PressAction
    ): ButtonWidget {
        return ButtonWidget.builder(label, action)
            .dimensions(x, y, width, layout.buttonHeight)
            .build().also { it.active = active }
    }

    // === Common Icon Buttons ===

    fun back(x: Int, y: Int, layout: ResponsiveLayout, action: ButtonWidget.PressAction): ButtonWidget =
        create(Text.literal("\u2190 Back"), x, y, layout.buttonWidth, layout, action) // ←

    fun close(x: Int, y: Int, layout: ResponsiveLayout, action: ButtonWidget.PressAction): ButtonWidget =
        small(Text.literal("\u2715"), x, y, layout, action) // ✕

    fun done(x: Int, y: Int, layout: ResponsiveLayout, action: ButtonWidget.PressAction): ButtonWidget =
        create(Text.literal("Done"), x, y, layout, action)

    fun add(x: Int, y: Int, layout: ResponsiveLayout, action: ButtonWidget.PressAction): ButtonWidget =
        small(Text.literal("+"), x, y, layout, action)

    fun remove(x: Int, y: Int, layout: ResponsiveLayout, action: ButtonWidget.PressAction): ButtonWidget =
        small(Text.literal("-"), x, y, layout, action)

    fun settings(x: Int, y: Int, layout: ResponsiveLayout, action: ButtonWidget.PressAction): ButtonWidget =
        small(Text.literal("\u2699"), x, y, layout, action) // ⚙

    fun refresh(x: Int, y: Int, layout: ResponsiveLayout, action: ButtonWidget.PressAction): ButtonWidget =
        small(Text.literal("\u21BB"), x, y, layout, action) // ↻
}
