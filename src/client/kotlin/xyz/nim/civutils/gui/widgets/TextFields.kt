package xyz.nim.civutils.gui.widgets

import net.minecraft.client.font.TextRenderer
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import net.minecraft.util.Formatting
import xyz.nim.civutils.gui.theme.ResponsiveLayout

/**
 * Factory functions for creating themed text fields.
 */
object TextFields {

    /**
     * Create a basic text field with custom dimensions.
     */
    fun create(
        textRenderer: TextRenderer,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        placeholder: String,
        maxLength: Int
    ): TextFieldWidget {
        return TextFieldWidget(textRenderer, x, y, width, height, Text.literal("")).apply {
            setPlaceholder(Text.literal(placeholder).formatted(Formatting.GRAY))
            setMaxLength(maxLength)
        }
    }

    /**
     * Create a text field with responsive height.
     */
    fun create(
        textRenderer: TextRenderer,
        x: Int,
        y: Int,
        width: Int,
        layout: ResponsiveLayout,
        placeholder: String,
        maxLength: Int
    ): TextFieldWidget {
        return create(textRenderer, x, y, width, layout.controlHeight, placeholder, maxLength)
    }

    /**
     * Create a search field (standard search input).
     */
    fun search(
        textRenderer: TextRenderer,
        x: Int,
        y: Int,
        width: Int,
        layout: ResponsiveLayout
    ): TextFieldWidget {
        return create(textRenderer, x, y, width, layout.controlHeight, "Search...", 64)
    }

    /**
     * Create a standard input field.
     */
    fun input(
        textRenderer: TextRenderer,
        x: Int,
        y: Int,
        width: Int,
        layout: ResponsiveLayout,
        placeholder: String,
        maxLength: Int
    ): TextFieldWidget {
        return create(textRenderer, x, y, width, layout.controlHeight, placeholder, maxLength)
    }

    /**
     * Create a code field (for abbreviations, codes, etc.).
     */
    fun code(
        textRenderer: TextRenderer,
        x: Int,
        y: Int,
        width: Int,
        layout: ResponsiveLayout,
        placeholder: String,
        maxLength: Int
    ): TextFieldWidget {
        return create(textRenderer, x, y, width, layout.controlHeight, placeholder, maxLength)
    }

    /**
     * Create a name field.
     */
    fun name(
        textRenderer: TextRenderer,
        x: Int,
        y: Int,
        width: Int,
        layout: ResponsiveLayout
    ): TextFieldWidget {
        return create(textRenderer, x, y, width, layout.controlHeight, "Name...", 64)
    }

    /**
     * Create a message field (for longer inputs).
     */
    fun message(
        textRenderer: TextRenderer,
        x: Int,
        y: Int,
        width: Int,
        layout: ResponsiveLayout,
        maxLength: Int
    ): TextFieldWidget {
        return create(textRenderer, x, y, width, layout.controlHeight, "Message...", maxLength)
    }

    /**
     * Configure a text field with a change listener.
     */
    fun withChangeListener(
        field: TextFieldWidget,
        listener: (String) -> Unit
    ): TextFieldWidget {
        field.setChangedListener(listener)
        return field
    }

    /**
     * Configure a text field to be focused.
     */
    fun focused(field: TextFieldWidget): TextFieldWidget {
        field.isFocused = true
        return field
    }
}
