package xyz.nim.civutils.gui.screens

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import xyz.nim.civutils.data.playertag.AttributeType
import xyz.nim.civutils.data.playertag.AttributeValue
import xyz.nim.civutils.models.PlayerTagModel
import xyz.nim.civutils.utils.PlayerHeadRenderer
import xyz.nim.civutils.utils.PlayerTagStyler
import xyz.nim.lib.ui.NlibTheme

/**
 * Quick tag popup for rapidly tagging players.
 * Supports all attribute types, not just trust levels.
 * Features:
 * - Number keys 1-9 for instant selection
 * - Tab to switch between attribute types
 * - Shows player head and current tag status
 * - Colored buttons matching attribute value colors
 *
 * Layout:
 * ┌─────────────────────────────┐
 * │     [Head] PlayerName       │
 * │     Current: ★ Allied       │
 * ├─────────────────────────────┤
 * │ Type: Trust Level    [Tab]  │
 * ├─────────────────────────────┤
 * │ [1] ⚔ Hostile               │
 * │ [2] ? Unknown               │
 * │ [3] • Neutral               │
 * │ [4] ✔ Trusted               │
 * │ [5] ★ Allied                │
 * ├─────────────────────────────┤
 * │ [C] Clear all tags          │
 * └─────────────────────────────┘
 */
class QuickTagScreen(
    private val playerName: String,
    private val playerUuid: String? = null
) : CivutilsScreen(Component.literal("Quick Tag")) {

    companion object {
        private const val PANEL_WIDTH = 220
        private const val BUTTON_HEIGHT = 24
        private const val BUTTON_SPACING = 4
    }

    private var panelX = 0
    private var panelY = 0
    private var panelHeight = 0

    private var attributeTypes: List<AttributeType> = emptyList()
    private var currentTypeIndex = 0
    private var currentType: AttributeType? = null
    private var currentTagValue: String? = null

    private val valueButtons = mutableListOf<ValueButton>()

    private data class ValueButton(
        val value: AttributeValue,
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val keyIndex: Int
    )

    override fun init() {
        super.init()

        // Load attribute types
        attributeTypes = PlayerTagModel.getAttributeTypes()
        if (attributeTypes.isEmpty()) {
            // Auto-add defaults if none exist
            PlayerTagModel.addDefaultAttributeTypes()
            attributeTypes = PlayerTagModel.getAttributeTypes()
        }

        // Find the trust type by default, or use first type
        currentTypeIndex = attributeTypes.indexOfFirst { it.id == "trust" }
        if (currentTypeIndex < 0) currentTypeIndex = 0
        currentType = attributeTypes.getOrNull(currentTypeIndex)

        // Get current tag value for this type
        val player = PlayerTagModel.getPlayer(playerName)
        currentTagValue = currentType?.let { player?.getAttribute(it.id) }

        // Calculate panel height based on number of values
        val headerHeight = 80 // Head, name, type selector
        val valuesHeight = (currentType?.values?.size ?: 0) * (BUTTON_HEIGHT + BUTTON_SPACING)
        val clearButtonHeight = BUTTON_HEIGHT + 20
        panelHeight = headerHeight + valuesHeight + clearButtonHeight

        // Center the panel
        panelX = (width - PANEL_WIDTH) / 2
        panelY = (height - panelHeight) / 2

        rebuildButtons()
    }

    private fun rebuildButtons() {
        clearWidgets()
        valueButtons.clear()

        val buttonWidth = PANEL_WIDTH - 20
        var buttonY = panelY + 80

        currentType?.let { type ->
            for ((index, value) in type.values.withIndex()) {
                val isSelected = currentTagValue == value.id
                val label = "[${index + 1}] ${value.style.prefix} ${value.displayName}".trim()

                val button = object : Button(
                    panelX + 10, buttonY, buttonWidth, BUTTON_HEIGHT,
                    Component.literal(label),
                    { selectValue(type.id, value) },
                    { supplier -> supplier.get() }
                ) {
                    override fun renderWidget(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
                        val color = value.style.color
                        val bgColor = if (isHovered) {
                            blendColor(color, 0x444444, 0.5f)
                        } else if (isSelected) {
                            blendColor(color, 0x222222, 0.6f)
                        } else {
                            blendColor(color, 0x1A1A1A, 0.2f)
                        }

                        // Background
                        guiGraphics.fill(x, y, x + width, y + height, bgColor or (0xFF shl 24))

                        // Border with value color
                        val borderColor = if (isSelected) color or (0xFF shl 24) else blendColor(color, 0x666666, 0.5f) or (0xFF shl 24)
                        guiGraphics.renderOutline(x, y, width, height, borderColor)

                        // Selected indicator
                        if (isSelected) {
                            guiGraphics.fill(x + 2, y + 2, x + 5, y + height - 2, color or (0xFF shl 24))
                        }

                        // Text with value color
                        val textColor = if (isSelected || isHovered) color else blendColor(color, 0xAAAAAA, 0.7f)
                        guiGraphics.drawString(font, message, x + 10, y + (height - 8) / 2, textColor, true)
                    }
                }

                addRenderableWidget(button)
                valueButtons.add(ValueButton(value, panelX + 10, buttonY, buttonWidth, BUTTON_HEIGHT, index))
                buttonY += BUTTON_HEIGHT + BUTTON_SPACING
            }
        }

        // Clear button
        buttonY += 10
        val clearButton = Button.builder(Component.literal("[C] Clear all tags")) {
            clearTags()
        }
            .bounds(panelX + 10, buttonY, buttonWidth, BUTTON_HEIGHT)
            .build()
        addRenderableWidget(clearButton)

        // Type switcher (if multiple types)
        if (attributeTypes.size > 1) {
            val typeBtn = Button.builder(Component.literal("[Tab] Switch Type")) {
                cycleType()
            }
                .bounds(panelX + PANEL_WIDTH - 100, panelY + 55, 90, 16)
                .build()
            addRenderableWidget(typeBtn)
        }
    }

    private fun blendColor(color1: Int, color2: Int, ratio: Float): Int {
        val r1 = (color1 shr 16) and 0xFF
        val g1 = (color1 shr 8) and 0xFF
        val b1 = color1 and 0xFF

        val r2 = (color2 shr 16) and 0xFF
        val g2 = (color2 shr 8) and 0xFF
        val b2 = color2 and 0xFF

        val r = (r1 * ratio + r2 * (1 - ratio)).toInt()
        val g = (g1 * ratio + g2 * (1 - ratio)).toInt()
        val b = (b1 * ratio + b2 * (1 - ratio)).toInt()

        return (r shl 16) or (g shl 8) or b
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Number keys 1-9 for quick selection
        val values = currentType?.values ?: emptyList()
        for ((index, value) in values.withIndex()) {
            if (index > 8) break // Only support 1-9
            if (keyCode == GLFW.GLFW_KEY_1 + index) {
                selectValue(currentType!!.id, value)
                return true
            }
        }

        // C for clear
        if (keyCode == GLFW.GLFW_KEY_C) {
            clearTags()
            return true
        }

        // Tab to cycle types
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleType()
            return true
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }

    private fun cycleType() {
        if (attributeTypes.isEmpty()) return
        currentTypeIndex = (currentTypeIndex + 1) % attributeTypes.size
        currentType = attributeTypes[currentTypeIndex]

        val player = PlayerTagModel.getPlayer(playerName)
        currentTagValue = currentType?.let { player?.getAttribute(it.id) }

        // Recalculate height and rebuild
        val headerHeight = 80
        val valuesHeight = (currentType?.values?.size ?: 0) * (BUTTON_HEIGHT + BUTTON_SPACING)
        val clearButtonHeight = BUTTON_HEIGHT + 20
        panelHeight = headerHeight + valuesHeight + clearButtonHeight
        panelY = (height - panelHeight) / 2

        rebuildButtons()
    }

    private fun selectValue(typeId: String, value: AttributeValue) {
        if (PlayerTagModel.setPlayerAttribute(playerName, typeId, value.id, playerUuid)) {
            toastManager.success("Tagged $playerName as ${value.displayName}")
        } else {
            toastManager.error("Failed to tag player")
        }

        minecraft?.execute {
            onClose()
        }
    }

    private fun clearTags() {
        val player = PlayerTagModel.getPlayer(playerName)
        if (player != null) {
            PlayerTagModel.untagPlayer(playerName)
            toastManager.success("Cleared tags from $playerName")
        } else {
            toastManager.error("$playerName has no tags")
        }

        minecraft?.execute {
            onClose()
        }
    }

    override fun renderPanels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Draw panel background
        drawPanel(guiGraphics, panelX, panelY, PANEL_WIDTH, panelHeight)

        // Draw header section
        guiGraphics.fill(panelX + 1, panelY + 1, panelX + PANEL_WIDTH - 1, panelY + 50, NlibTheme.HEADER_BG)

        // Player head
        PlayerHeadRenderer.renderHeadWithBorder(
            guiGraphics, playerName, playerUuid,
            panelX + 10, panelY + 10, 32,
            PlayerTagStyler.getPrimaryColor(playerName)
        )

        // Player name
        guiGraphics.drawString(font, playerName, panelX + 50, panelY + 12, NlibTheme.TEXT_PRIMARY, true)

        // Current tag status
        val currentText = if (currentTagValue != null) {
            val value = currentType?.getValue(currentTagValue!!)
            if (value != null) {
                "Current: ${value.style.prefix} ${value.displayName}".trim()
            } else {
                "Current: $currentTagValue"
            }
        } else {
            "Not tagged"
        }
        val currentColor = currentType?.getValue(currentTagValue ?: "")?.style?.color ?: 0x888888
        guiGraphics.drawString(font, currentText, panelX + 50, panelY + 26, currentColor, false)

        // Type selector line
        guiGraphics.fill(panelX + 1, panelY + 50, panelX + PANEL_WIDTH - 1, panelY + 75, NlibTheme.BACKGROUND_LIGHT)

        // Type name
        val typeName = currentType?.displayName ?: "No types"
        guiGraphics.drawString(font, typeName, panelX + 10, panelY + 58, NlibTheme.TEXT_PRIMARY, false)
    }

    override fun renderOverlays(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Nothing special needed here
    }
}
