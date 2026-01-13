package xyz.nim.civutils.gui.screens

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.network.chat.Component
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.value
import xyz.nim.civutils.core.feature.Feature
import xyz.nim.civutils.core.overlay.Overlay
import xyz.nim.lib.config.ConfigOption
import xyz.nim.lib.config.options.*
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.lib.ui.components.ColorInput
import xyz.nim.lib.ui.components.NlibListWidget
import xyz.nim.civutils.gui.widgets.*

/**
 * Main configuration screen for CivUtils.
 * Uses the CivutilsScreen base class for theming and features.
 */
class ConfigScreen : CivutilsScreen(Component.literal("CivUtils Configuration")) {

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
    private var featureList: FeatureListWidget? = null
    private var overlayList: OverlayListWidget? = null
    private val configWidgets = mutableListOf<net.minecraft.client.gui.components.AbstractWidget>()

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
        addRenderableWidget(
            Button.builder(Component.literal("Features")) {
                currentTab = Tab.FEATURES
                selectedFeature = null
                updateListVisibility()
                rebuildConfigWidgets()
            }
                .bounds(width / 2 - tabWidth - 5, tabY, tabWidth, 20)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Overlays")) {
                currentTab = Tab.OVERLAYS
                selectedOverlay = null
                updateListVisibility()
                rebuildConfigWidgets()
            }
                .bounds(width / 2 + 5, tabY, tabWidth, 20)
                .build()
        )

        // Feature list - positioned below panel header
        val listHeaderOffset = 20  // Space for panel title
        val listY = contentY + listHeaderOffset
        val listHeight = contentHeight - listHeaderOffset

        featureList = FeatureListWidget(minecraft!!, leftPanelWidth, listHeight, listY, 36) { feature ->
            selectedFeature = feature
            rebuildConfigWidgets()
        }
        featureList?.setX(leftPanelX)
        for (feature in CivutilsMod.featureManager.getFeatures()) {
            featureList?.addEntryToList(FeatureEntry(feature))
        }
        addWidget(featureList)

        // Overlay list - positioned below panel header
        overlayList = OverlayListWidget(minecraft!!, leftPanelWidth, listHeight, listY, 36) { overlay ->
            selectedOverlay = overlay
            rebuildConfigWidgets()
        }
        overlayList?.setX(leftPanelX)
        for (overlay in CivutilsMod.overlayManager.getOverlays()) {
            overlayList?.addEntryToList(OverlayEntry(overlay))
        }
        addWidget(overlayList)

        // Set initial visibility
        updateListVisibility()

        // Overlay Editor button - top right
        addRenderableWidget(
            Button.builder(Component.literal("Open Overlay Editor")) {
                minecraft?.setScreen(OverlayEditorScreen())
            }
                .bounds(width - 150 - layout.margin, 5, 150, 20)
                .build()
        )
    }

    private fun updateListVisibility() {
        featureList?.visible = (currentTab == Tab.FEATURES)
        overlayList?.visible = (currentTab == Tab.OVERLAYS)
    }

    private fun rebuildConfigWidgets() {
        // Remove old config widgets
        configWidgets.forEach { removeWidget(it) }
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
                    addRenderableWidget(toggle)
                    configWidgets.add(toggle)
                    y += 30

                    // Other configs
                    for (config in feature.getConfigs()) {
                        if (config === feature.userEnabled) continue
                        val widget = createWidgetForConfig(config, rightPanelX + 10, y, widgetWidth)
                        if (widget != null) {
                            addRenderableWidget(widget)
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
                    addRenderableWidget(toggle)
                    configWidgets.add(toggle)
                    y += 30

                    // Other configs
                    for (config in overlay.getConfigs()) {
                        if (config === overlay.enabled) continue
                        val widget = createWidgetForConfig(config, rightPanelX + 10, y, widgetWidth)
                        if (widget != null) {
                            addRenderableWidget(widget)
                            configWidgets.add(widget)
                            y += 30
                        }
                    }

                    // Edit Position button
                    val editBtn = Button.builder(Component.literal("Edit Position")) {
                        minecraft?.setScreen(OverlayEditorScreen())
                    }
                        .bounds(rightPanelX + 10, y, widgetWidth, 20)
                        .build()
                    addRenderableWidget(editBtn)
                    configWidgets.add(editBtn)
                }
            }
        }
    }

    private fun createWidgetForConfig(config: ConfigOption<*>, x: Int, y: Int, width: Int): net.minecraft.client.gui.components.AbstractWidget? {
        val displayName = config.getDisplayName()
        val name = if (displayName.isNullOrEmpty()) {
            config.getName().replaceFirstChar { c -> c.uppercase() }
                .replace(Regex("([A-Z])"), " $1").trim()
        } else {
            displayName
        }

        return when (config) {
            is BooleanConfig -> ToggleButton(x, y, width, 20, config, name)
            is IntegerConfig -> IntSlider(x, y, width, 20, config, name)
            is ColorConfig -> ColorInput(x, y, width, 20, config.getValue()) { newColor ->
                config.setValue(newColor)
            }
            is OptionListConfig<*> -> {
                val values = config.getAllowedValues()
                val currentValueEnum = config.getValue() as Enum<*>
                var currentIndex = values.indexOfFirst { (it as Enum<*>).name == currentValueEnum.name }
                if (currentIndex < 0) currentIndex = 0

                Button.builder(Component.literal("$name: ${currentValueEnum.name}")) { btn ->
                    currentIndex = (currentIndex + 1) % values.size
                    val nextValue = values[currentIndex] as Enum<*>
                    config.setFromName(nextValue.name)
                    btn.message = Component.literal("$name: ${nextValue.name}")
                }
                    .bounds(x, y, width, 20)
                    .build()
            }
            else -> null
        }
    }


    override fun renderPanels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Title at top center
        guiGraphics.drawCenteredString(font, title, width / 2, 8, NlibTheme.TEXT_PRIMARY)

        // Tab indicator (underline for selected tab)
        val tabY = 25 + 20 + 2  // Below the tab buttons
        val indicatorX = if (currentTab == Tab.FEATURES) width / 2 - 105 else width / 2 + 5
        guiGraphics.fill(indicatorX, tabY, indicatorX + 100, tabY + 2, NlibTheme.ACCENT)

        // Panel backgrounds using theme colors
        drawPanel(guiGraphics, leftPanelX, contentY, leftPanelWidth, contentHeight)
        drawPanel(guiGraphics, rightPanelX, contentY, rightPanelWidth, contentHeight)

        // Render the appropriate list
        when (currentTab) {
            Tab.FEATURES -> featureList?.render(guiGraphics, mouseX, mouseY, partialTick)
            Tab.OVERLAYS -> overlayList?.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }

    override fun renderOverlays(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Draw panel headers on top of content
        val leftTitle = if (currentTab == Tab.FEATURES) "Features" else "Overlays"
        guiGraphics.drawString(font, leftTitle, leftPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)

        val rightTitle = when (currentTab) {
            Tab.FEATURES -> selectedFeature?.displayName ?: "Select a feature"
            Tab.OVERLAYS -> selectedOverlay?.displayName ?: "Select an overlay"
        }
        guiGraphics.drawString(font, rightTitle, rightPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)

        // Render color picker popups on top of everything
        for (widget in configWidgets) {
            if (widget is ColorInput) {
                widget.renderPickerPopup(guiGraphics, mouseX, mouseY)
            }
        }
    }

    override fun onClose() {
        CivutilsMod.configManager.saveAll()
        toastManager.success("Configuration saved")
        super.onClose()
    }

    // === List Widgets ===

    private inner class FeatureListWidget(
        client: Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int,
        private val onSelect: (Feature) -> Unit
    ) : NlibListWidget<FeatureEntry>(client, width, height, y, itemHeight) {

        override fun setSelected(entry: FeatureEntry?) {
            super.setSelected(entry)
            entry?.feature?.let { onSelect(it) }
        }
    }

    private inner class FeatureEntry(val feature: Feature) : NlibListWidget.Entry<FeatureEntry>() {
        override fun renderContent(
            guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float
        ) {
            val x = getX()
            val y = getY()
            val entryWidth = getWidth()
            val entryHeight = getHeight()
            val selected = (featureList?.selected === this)
            renderBackground(guiGraphics, x, y, entryWidth, entryHeight, hovered, selected)

            val statusColor = if (feature.enabled) Colors.ENABLED else Colors.DISABLED
            guiGraphics.fill(x + 4, y + 4, x + 8, y + entryHeight - 4, statusColor)

            val font = minecraft?.font ?: return
            guiGraphics.drawString(font, feature.displayName, x + 14, y + 6, Colors.TEXT, true)
            guiGraphics.drawString(font, "§7${feature.category.name.lowercase()}", x + 14, y + 18, Colors.TEXT_SECONDARY, false)
        }
    }

    private inner class OverlayListWidget(
        client: Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int,
        private val onSelect: (Overlay) -> Unit
    ) : NlibListWidget<OverlayEntry>(client, width, height, y, itemHeight) {

        override fun setSelected(entry: OverlayEntry?) {
            super.setSelected(entry)
            entry?.overlay?.let { onSelect(it) }
        }
    }

    private inner class OverlayEntry(val overlay: Overlay) : NlibListWidget.Entry<OverlayEntry>() {
        override fun renderContent(
            guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float
        ) {
            val x = getX()
            val y = getY()
            val entryWidth = getWidth()
            val entryHeight = getHeight()
            val selected = (overlayList?.selected === this)
            renderBackground(guiGraphics, x, y, entryWidth, entryHeight, hovered, selected)

            val statusColor = if (overlay.enabled.value) Colors.ENABLED else Colors.DISABLED
            guiGraphics.fill(x + 4, y + 4, x + 8, y + entryHeight - 4, statusColor)

            val font = minecraft?.font ?: return
            guiGraphics.drawString(font, overlay.displayName, x + 14, y + 6, Colors.TEXT, true)
            val posText = "§7${overlay.position.anchorSection.name.lowercase().replace("_", " ")}"
            guiGraphics.drawString(font, posText, x + 14, y + 18, Colors.TEXT_SECONDARY, false)
        }
    }
}
