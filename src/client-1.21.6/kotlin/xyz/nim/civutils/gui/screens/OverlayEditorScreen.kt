package xyz.nim.civutils.gui.screens

import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.Renderable
import net.minecraft.network.chat.Component
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.overlay.*
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.civutils.gui.widgets.Colors
import xyz.nim.civutils.utils.renderOutline

/**
 * Visual editor for positioning overlays on screen.
 * Allows dragging overlays and snapping to anchor points.
 * Uses CivutilsScreen for toast notifications and confirm dialogs.
 */
class OverlayEditorScreen : CivutilsScreen(Component.literal("Overlay Editor")) {

    private var selectedOverlay: Overlay? = null
    private var isDragging = false
    private var dragOffsetX = 0
    private var dragOffsetY = 0

    private var showGrid = true
    private var snapToGrid = true
    private val gridSize = 10

    override fun init() {
        super.init()

        // Done button
        addRenderableWidget(
            Button.builder(Component.literal("Done")) {
                CivutilsMod.configManager.saveAll()
                toastManager.success("Overlay positions saved")
                onClose()
            }
                .bounds(width / 2 - 50, height - 30, 100, layout.buttonHeight)
                .build()
        )

        // Toggle grid button
        addRenderableWidget(
            Button.builder(Component.literal("Grid: ${if (showGrid) "ON" else "OFF"}")) { btn ->
                showGrid = !showGrid
                btn.message = Component.literal("Grid: ${if (showGrid) "ON" else "OFF"}")
            }
                .bounds(layout.margin, height - 30, 80, layout.buttonHeight)
                .build()
        )

        // Toggle snap button
        addRenderableWidget(
            Button.builder(Component.literal("Snap: ${if (snapToGrid) "ON" else "OFF"}")) { btn ->
                snapToGrid = !snapToGrid
                btn.message = Component.literal("Snap: ${if (snapToGrid) "ON" else "OFF"}")
            }
                .bounds(layout.margin + 90, height - 30, 80, layout.buttonHeight)
                .build()
        )

        // Back to config button
        addRenderableWidget(
            Button.builder(Component.literal("\u2190 Config")) {
                minecraft?.setScreen(ConfigScreen())
            }
                .bounds(layout.margin, layout.margin, 80, layout.buttonHeight)
                .build()
        )
    }

    // Override the full render since we want a lighter background for the overlay editor
    override fun render(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Lighter semi-transparent background to see game
        guiGraphics.fill(0, 0, width, height, 0x66000000)

        // Draw grid
        if (showGrid) {
            drawGrid(guiGraphics)
        }

        // Draw anchor section indicators
        drawAnchorSections(guiGraphics)

        // Render all overlay previews
        for (overlay in CivutilsMod.overlayManager.getOverlays()) {
            if (!overlay.enabled.value) continue

            val isSelected = overlay === selectedOverlay
            val x = overlay.getRenderX()
            val y = overlay.getRenderY()
            val w = overlay.size.width
            val h = overlay.size.height

            // Draw selection/hover box
            val isHovered = !isDragging && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h

            if (isSelected) {
                // Selection outline
                guiGraphics.renderOutline(x - 2, y - 2, w + 4, h + 4, NlibTheme.ACCENT)
                // Corner handles
                drawHandle(guiGraphics, x - 4, y - 4)
                drawHandle(guiGraphics, x + w, y - 4)
                drawHandle(guiGraphics, x - 4, y + h)
                drawHandle(guiGraphics, x + w, y + h)
            } else if (isHovered) {
                guiGraphics.renderOutline(x - 1, y - 1, w + 2, h + 2, NlibTheme.TEXT_SECONDARY)
            }

            // Background for overlay area
            guiGraphics.fill(x, y, x + w, y + h, 0x44FFFFFF)

            // Render overlay preview
            overlay.renderPreview(guiGraphics, partialTick)
        }

        // Draw info for selected overlay
        selectedOverlay?.let { overlay ->
            val infoY = 40
            guiGraphics.drawString(font, "§e${overlay.displayName}", width / 2 - 100, infoY, NlibTheme.TEXT_PRIMARY, true)
            guiGraphics.drawString(font, "§7Position: ${overlay.position.anchorSection}", width / 2 - 100, infoY + 12, NlibTheme.TEXT_PRIMARY, false)
            guiGraphics.drawString(font, "§7Offset: ${overlay.position.offsetX}, ${overlay.position.offsetY}", width / 2 - 100, infoY + 24, NlibTheme.TEXT_PRIMARY, false)
            guiGraphics.drawString(font, "§7Size: ${overlay.size.width}x${overlay.size.height}", width / 2 - 100, infoY + 36, NlibTheme.TEXT_PRIMARY, false)
        }

        // Instructions
        val instructions = listOf(
            "§7Click an overlay to select it",
            "§7Drag to move",
            "§7Press §fESC§7 to exit"
        )
        var iy = layout.margin
        for (line in instructions) {
            guiGraphics.drawString(font, line, width - font.width(line.replace("§.", "")) - layout.margin, iy, NlibTheme.TEXT_PRIMARY, false)
            iy += 12
        }

        // Render widgets
        for (element in children()) {
            if (element is Renderable) {
                element.render(guiGraphics, mouseX, mouseY, partialTick)
            }
        }

        // Render toasts
        toastManager.render(guiGraphics, font, width, height)
    }

    private fun drawGrid(guiGraphics: GuiGraphics) {
        val gridColor = 0x22FFFFFF

        // Vertical lines
        for (x in 0 until width step gridSize) {
            guiGraphics.fill(x, 0, x + 1, height, gridColor)
        }

        // Horizontal lines
        for (y in 0 until height step gridSize) {
            guiGraphics.fill(0, y, width, y + 1, gridColor)
        }

        // Center lines (more visible)
        guiGraphics.fill(width / 2, 0, width / 2 + 1, height, 0x44FFFFFF)
        guiGraphics.fill(0, height / 2, width, height / 2 + 1, 0x44FFFFFF)
    }

    private fun drawAnchorSections(guiGraphics: GuiGraphics) {
        val thirdW = width / 3
        val thirdH = height / 3

        // Draw section dividers
        val dividerColor = 0x33FFFFFF

        // Vertical dividers
        guiGraphics.fill(thirdW, 0, thirdW + 1, height, dividerColor)
        guiGraphics.fill(thirdW * 2, 0, thirdW * 2 + 1, height, dividerColor)

        // Horizontal dividers
        guiGraphics.fill(0, thirdH, width, thirdH + 1, dividerColor)
        guiGraphics.fill(0, thirdH * 2, width, thirdH * 2 + 1, dividerColor)

        // Draw anchor point markers
        for (section in AnchorSection.entries) {
            val anchorX = section.getAnchorX(width)
            val anchorY = section.getAnchorY(height)

            // Small cross at anchor point
            guiGraphics.fill(anchorX - 5, anchorY, anchorX + 6, anchorY + 1, NlibTheme.ACCENT)
            guiGraphics.fill(anchorX, anchorY - 5, anchorX + 1, anchorY + 6, NlibTheme.ACCENT)
        }
    }

    private fun drawHandle(guiGraphics: GuiGraphics, x: Int, y: Int) {
        guiGraphics.fill(x, y, x + 6, y + 6, NlibTheme.ACCENT)
        guiGraphics.renderOutline(x, y, 6, 6, NlibTheme.TEXT_PRIMARY)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (super.mouseClicked(mouseX, mouseY, button)) return true

        if (button == 0) {
            // Find clicked overlay
            for (overlay in CivutilsMod.overlayManager.getOverlays().reversed()) {
                if (!overlay.enabled.value) continue

                val x = overlay.getRenderX()
                val y = overlay.getRenderY()
                val w = overlay.size.width
                val h = overlay.size.height

                if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h) {
                    selectedOverlay = overlay
                    isDragging = true
                    dragOffsetX = mouseX.toInt() - x
                    dragOffsetY = mouseY.toInt() - y
                    return true
                }
            }

            // Clicked empty space - deselect
            selectedOverlay = null
        }

        return false
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        if (button == 0 && isDragging) {
            isDragging = false

            // Snap to nearest anchor section
            selectedOverlay?.let { overlay ->
                val centerX = overlay.getRenderX() + overlay.size.width / 2
                val centerY = overlay.getRenderY() + overlay.size.height / 2

                // Find nearest anchor section
                var nearestSection = overlay.position.anchorSection
                var nearestDist = Double.MAX_VALUE

                for (section in AnchorSection.entries) {
                    val anchorX = section.getAnchorX(width)
                    val anchorY = section.getAnchorY(height)
                    val dist = kotlin.math.sqrt(
                        ((centerX - anchorX) * (centerX - anchorX) + (centerY - anchorY) * (centerY - anchorY)).toDouble()
                    )
                    if (dist < nearestDist) {
                        nearestDist = dist
                        nearestSection = section
                    }
                }

                // Update position relative to new anchor
                val newAnchorX = nearestSection.getAnchorX(width)
                val newAnchorY = nearestSection.getAnchorY(height)

                // Calculate offset based on alignment
                var offsetX = overlay.getRenderX() - newAnchorX
                var offsetY = overlay.getRenderY() - newAnchorY

                // Adjust for alignment
                when (overlay.position.horizontalAlignment) {
                    HorizontalAlignment.CENTER -> offsetX += overlay.size.width / 2
                    HorizontalAlignment.RIGHT -> offsetX += overlay.size.width
                    else -> {}
                }
                when (overlay.position.verticalAlignment) {
                    VerticalAlignment.MIDDLE -> offsetY += overlay.size.height / 2
                    VerticalAlignment.BOTTOM -> offsetY += overlay.size.height
                    else -> {}
                }

                overlay.position.anchorSection = nearestSection
                overlay.position.offsetX = if (snapToGrid) (offsetX / gridSize) * gridSize else offsetX
                overlay.position.offsetY = if (snapToGrid) (offsetY / gridSize) * gridSize else offsetY

                CivutilsMod.configManager.markDirty()
            }
        }

        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        if (isDragging && selectedOverlay != null) {
            val overlay = selectedOverlay!!

            // Calculate new position
            var newX = mouseX.toInt() - dragOffsetX
            var newY = mouseY.toInt() - dragOffsetY

            // Snap to grid while dragging
            if (snapToGrid) {
                newX = (newX / gridSize) * gridSize
                newY = (newY / gridSize) * gridSize
            }

            // Keep on screen
            newX = newX.coerceIn(0, width - overlay.size.width)
            newY = newY.coerceIn(0, height - overlay.size.height)

            // Calculate offset from current anchor
            val anchorX = overlay.position.anchorSection.getAnchorX(width)
            val anchorY = overlay.position.anchorSection.getAnchorY(height)

            var offsetX = newX - anchorX
            var offsetY = newY - anchorY

            // Adjust for alignment
            when (overlay.position.horizontalAlignment) {
                HorizontalAlignment.CENTER -> offsetX += overlay.size.width / 2
                HorizontalAlignment.RIGHT -> offsetX += overlay.size.width
                else -> {}
            }
            when (overlay.position.verticalAlignment) {
                VerticalAlignment.MIDDLE -> offsetY += overlay.size.height / 2
                VerticalAlignment.BOTTOM -> offsetY += overlay.size.height
                else -> {}
            }

            overlay.position.offsetX = offsetX
            overlay.position.offsetY = offsetY

            return true
        }

        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun keyPressed(keyCode: Int, scanCode: Int, modifiers: Int): Boolean {
        // Delete selected overlay's position (reset to default)
        if (keyCode == 261 && selectedOverlay != null) { // Delete key
            selectedOverlay?.let {
                it.position.offsetX = 0
                it.position.offsetY = 0
                CivutilsMod.configManager.markDirty()
            }
            return true
        }

        // Arrow keys for fine adjustment
        selectedOverlay?.let { overlay ->
            val step = if (modifiers and 1 != 0) 10 else 1 // Shift for larger steps
            when (keyCode) {
                265 -> { overlay.position.offsetY -= step; return true } // Up
                264 -> { overlay.position.offsetY += step; return true } // Down
                263 -> { overlay.position.offsetX -= step; return true } // Left
                262 -> { overlay.position.offsetX += step; return true } // Right
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers)
    }
}
