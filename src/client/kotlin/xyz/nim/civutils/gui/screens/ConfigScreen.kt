package xyz.nim.civutils.gui.screens

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.feature.Feature
import xyz.nim.civutils.core.overlay.Overlay
import xyz.nim.civutils.gui.theme.CivutilsTheme
import xyz.nim.civutils.gui.widgets.*

/**
 * Main configuration screen for CivUtils.
 * Uses the CivutilsScreen base class for theming and features.
 */
class ConfigScreen : CivutilsScreen(Text.literal("CivUtils Configuration")) {

    private enum class Tab {
        FEATURES,
        OVERLAYS
    }

    private var currentTab = Tab.FEATURES
    private var selectedFeature: Feature? = null
    private var selectedOverlay: Overlay? = null

    // UI regions
    private var leftPanelX = 0
    private var leftPanelWidth = 0
    private var rightPanelX = 0
    private var rightPanelWidth = 0
    private var contentY = 0
    private var contentHeight = 0

    // Widgets
    private var featureList: ScrollableList<Feature>? = null
    private var overlayList: ScrollableList<Overlay>? = null
    private val configWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()

    override fun init() {
        super.init()

        // Calculate layout - more generous spacing for better appearance
        val headerAreaHeight = 50  // Space for title + tabs
        leftPanelWidth = (width * 0.35).toInt()
        leftPanelX = layout.margin
        rightPanelX = leftPanelX + leftPanelWidth + layout.spacing
        rightPanelWidth = width - rightPanelX - layout.margin
        contentY = headerAreaHeight
        contentHeight = height - contentY - layout.margin

        // Tab buttons - centered below title
        val tabWidth = 100
        val tabY = 25  // Below the title
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Features")) {
                currentTab = Tab.FEATURES
                selectedFeature = null
                rebuildConfigWidgets()
            }
                .dimensions(width / 2 - tabWidth - 5, tabY, tabWidth, 20)
                .build()
        )

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Overlays")) {
                currentTab = Tab.OVERLAYS
                selectedOverlay = null
                rebuildConfigWidgets()
            }
                .dimensions(width / 2 + 5, tabY, tabWidth, 20)
                .build()
        )

        // Feature list - positioned below panel header
        val listHeaderOffset = 20  // Space for panel title
        featureList = ScrollableList(
            x = leftPanelX,
            y = contentY + listHeaderOffset,
            width = leftPanelWidth,
            height = contentHeight - listHeaderOffset,
            itemHeight = 36,
            renderEntry = ::renderFeatureEntry,
            onEntryClick = { feature, _ ->
                selectedFeature = feature
                rebuildConfigWidgets()
            }
        )
        featureList?.setEntries(CivutilsMod.featureManager.getFeatures().toList())

        // Overlay list - positioned below panel header
        overlayList = ScrollableList(
            x = leftPanelX,
            y = contentY + listHeaderOffset,
            width = leftPanelWidth,
            height = contentHeight - listHeaderOffset,
            itemHeight = 36,
            renderEntry = ::renderOverlayEntry,
            onEntryClick = { overlay, _ ->
                selectedOverlay = overlay
                rebuildConfigWidgets()
            }
        )
        overlayList?.setEntries(CivutilsMod.overlayManager.getOverlays().toList())

        // Overlay Editor button - top right
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Open Overlay Editor")) {
                client?.setScreen(OverlayEditorScreen())
            }
                .dimensions(width - 150 - layout.margin, 5, 150, 20)
                .build()
        )
    }

    private fun rebuildConfigWidgets() {
        // Remove old config widgets
        configWidgets.forEach { remove(it) }
        configWidgets.clear()

        val startY = contentY + 24  // Below panel header
        val widgetWidth = rightPanelWidth - 20
        var y = startY

        when (currentTab) {
            Tab.FEATURES -> {
                selectedFeature?.let { feature ->
                    // Enable/Disable toggle
                    val toggle = ToggleButton(
                        rightPanelX + 10, y, widgetWidth, 20,
                        feature.userEnabled, "Enabled"
                    )
                    addDrawableChild(toggle)
                    configWidgets.add(toggle)
                    y += 30

                    // Other configs
                    for (config in feature.getConfigs()) {
                        if (config === feature.userEnabled) continue
                        val widget = createWidgetForConfig(config, rightPanelX + 10, y, widgetWidth)
                        if (widget != null) {
                            addDrawableChild(widget)
                            configWidgets.add(widget)
                            y += 30
                        }
                    }
                }
            }
            Tab.OVERLAYS -> {
                selectedOverlay?.let { overlay ->
                    // Enable/Disable toggle
                    val toggle = ToggleButton(
                        rightPanelX + 10, y, widgetWidth, 20,
                        overlay.enabled, "Enabled"
                    )
                    addDrawableChild(toggle)
                    configWidgets.add(toggle)
                    y += 30

                    // Other configs
                    for (config in overlay.getConfigs()) {
                        if (config === overlay.enabled) continue
                        val widget = createWidgetForConfig(config, rightPanelX + 10, y, widgetWidth)
                        if (widget != null) {
                            addDrawableChild(widget)
                            configWidgets.add(widget)
                            y += 30
                        }
                    }

                    // Edit Position button
                    val editBtn = ButtonWidget.builder(Text.literal("Edit Position")) {
                        client?.setScreen(OverlayEditorScreen())
                    }
                        .dimensions(rightPanelX + 10, y, widgetWidth, 20)
                        .build()
                    addDrawableChild(editBtn)
                    configWidgets.add(editBtn)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createWidgetForConfig(config: Config<*>, x: Int, y: Int, width: Int): net.minecraft.client.gui.widget.ClickableWidget? {
        val name = config.fieldName.replaceFirstChar { it.uppercase() }
            .replace(Regex("([A-Z])"), " $1").trim()

        return when (config.defaultValue) {
            is Boolean -> ToggleButton(x, y, width, 20, config as Config<Boolean>, name)
            is Int -> IntSlider(x, y, width, 20, config as Config<Int>, name, 0, 100)
            is Enum<*> -> {
                val enumConfig = config as Config<Enum<*>>
                val values = enumConfig.getValidLiterals() ?: return null
                var currentIndex = values.indexOf(enumConfig.value.name)

                ButtonWidget.builder(Text.literal("$name: ${enumConfig.value.name}")) { btn ->
                    currentIndex = (currentIndex + 1) % values.size
                    enumConfig.tryParseString(values[currentIndex])?.let {
                        enumConfig.value = it
                        btn.message = Text.literal("$name: ${enumConfig.value.name}")
                    }
                }
                    .dimensions(x, y, width, 20)
                    .build()
            }
            else -> null
        }
    }

    private fun renderFeatureEntry(
        context: DrawContext,
        feature: Feature,
        x: Int, y: Int, width: Int, height: Int,
        isSelected: Boolean, isHovered: Boolean
    ) {
        val bgColor = when {
            isSelected -> Colors.ACCENT
            isHovered -> Colors.BACKGROUND_HOVER
            else -> Colors.BACKGROUND_LIGHT
        }
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, bgColor)

        val statusColor = if (feature.enabled) Colors.ENABLED else Colors.DISABLED
        context.fill(x + 4, y + 4, x + 8, y + height - 4, statusColor)

        val textRenderer = client!!.textRenderer
        context.drawText(textRenderer, feature.displayName, x + 14, y + 6, Colors.TEXT, true)

        val categoryText = "§7${feature.category.name.lowercase()}"
        context.drawText(textRenderer, categoryText, x + 14, y + 18, Colors.TEXT_SECONDARY, false)
    }

    private fun renderOverlayEntry(
        context: DrawContext,
        overlay: Overlay,
        x: Int, y: Int, width: Int, height: Int,
        isSelected: Boolean, isHovered: Boolean
    ) {
        val bgColor = when {
            isSelected -> Colors.ACCENT
            isHovered -> Colors.BACKGROUND_HOVER
            else -> Colors.BACKGROUND_LIGHT
        }
        context.fill(x + 2, y + 2, x + width - 2, y + height - 2, bgColor)

        val statusColor = if (overlay.enabled.value) Colors.ENABLED else Colors.DISABLED
        context.fill(x + 4, y + 4, x + 8, y + height - 4, statusColor)

        val textRenderer = client!!.textRenderer
        context.drawText(textRenderer, overlay.displayName, x + 14, y + 6, Colors.TEXT, true)

        val posText = "§7${overlay.position.anchorSection.name.lowercase().replace("_", " ")}"
        context.drawText(textRenderer, posText, x + 14, y + 18, Colors.TEXT_SECONDARY, false)
    }

    override fun renderPanels(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Title at top center
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, CivutilsTheme.TEXT_PRIMARY)

        // Tab indicator (underline for selected tab)
        val tabY = 25 + 20 + 2  // Below the tab buttons
        val indicatorX = if (currentTab == Tab.FEATURES) width / 2 - 105 else width / 2 + 5
        context.fill(indicatorX, tabY, indicatorX + 100, tabY + 2, CivutilsTheme.ACCENT)

        // Panel backgrounds using theme colors
        drawPanel(context, leftPanelX, contentY, leftPanelWidth, contentHeight)
        drawPanel(context, rightPanelX, contentY, rightPanelWidth, contentHeight)

        // Render lists inside panels
        when (currentTab) {
            Tab.FEATURES -> featureList?.render(context, mouseX, mouseY, delta)
            Tab.OVERLAYS -> overlayList?.render(context, mouseX, mouseY, delta)
        }
    }

    override fun renderOverlays(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Draw panel headers on top of content
        val leftTitle = if (currentTab == Tab.FEATURES) "Features" else "Overlays"
        context.drawText(textRenderer, leftTitle, leftPanelX + 8, contentY + 6, CivutilsTheme.TEXT_PRIMARY, true)

        val rightTitle = when (currentTab) {
            Tab.FEATURES -> selectedFeature?.displayName ?: "Select a feature"
            Tab.OVERLAYS -> selectedOverlay?.displayName ?: "Select an overlay"
        }
        context.drawText(textRenderer, rightTitle, rightPanelX + 8, contentY + 6, CivutilsTheme.TEXT_PRIMARY, true)
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        when (currentTab) {
            Tab.FEATURES -> if (featureList?.mouseClicked(mouseX, mouseY, button) == true) return true
            Tab.OVERLAYS -> if (overlayList?.mouseClicked(mouseX, mouseY, button) == true) return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseReleased(mouseX: Double, mouseY: Double, button: Int): Boolean {
        featureList?.mouseReleased(mouseX, mouseY, button)
        overlayList?.mouseReleased(mouseX, mouseY, button)
        return super.mouseReleased(mouseX, mouseY, button)
    }

    override fun mouseDragged(mouseX: Double, mouseY: Double, button: Int, deltaX: Double, deltaY: Double): Boolean {
        when (currentTab) {
            Tab.FEATURES -> if (featureList?.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) == true) return true
            Tab.OVERLAYS -> if (overlayList?.mouseDragged(mouseX, mouseY, button, deltaX, deltaY) == true) return true
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        when (currentTab) {
            Tab.FEATURES -> if (featureList?.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) == true) return true
            Tab.OVERLAYS -> if (overlayList?.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount) == true) return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun close() {
        CivutilsMod.configManager.saveAll()
        toastManager.success("Configuration saved")
        super.close()
    }
}
