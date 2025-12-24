package xyz.nim.civutils.gui.screens

import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.text.Text
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.core.config.Config
import xyz.nim.civutils.core.feature.Feature
import xyz.nim.civutils.core.overlay.Overlay
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.lib.ui.components.NlibListWidget
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
    private var featureList: FeatureListWidget? = null
    private var overlayList: OverlayListWidget? = null
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
                updateListVisibility()
                rebuildConfigWidgets()
            }
                .dimensions(width / 2 - tabWidth - 5, tabY, tabWidth, 20)
                .build()
        )

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Overlays")) {
                currentTab = Tab.OVERLAYS
                selectedOverlay = null
                updateListVisibility()
                rebuildConfigWidgets()
            }
                .dimensions(width / 2 + 5, tabY, tabWidth, 20)
                .build()
        )

        // Feature list - positioned below panel header
        val listHeaderOffset = 20  // Space for panel title
        val listY = contentY + listHeaderOffset
        val listHeight = contentHeight - listHeaderOffset

        featureList = FeatureListWidget(client!!, leftPanelWidth, listHeight, listY, 36) { feature ->
            selectedFeature = feature
            rebuildConfigWidgets()
        }
        featureList?.setX(leftPanelX)
        for (feature in CivutilsMod.featureManager.getFeatures()) {
            featureList?.addEntryToList(FeatureEntry(feature))
        }
        addSelectableChild(featureList)

        // Overlay list - positioned below panel header
        overlayList = OverlayListWidget(client!!, leftPanelWidth, listHeight, listY, 36) { overlay ->
            selectedOverlay = overlay
            rebuildConfigWidgets()
        }
        overlayList?.setX(leftPanelX)
        for (overlay in CivutilsMod.overlayManager.getOverlays()) {
            overlayList?.addEntryToList(OverlayEntry(overlay))
        }
        addSelectableChild(overlayList)

        // Set initial visibility
        updateListVisibility()

        // Overlay Editor button - top right
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Open Overlay Editor")) {
                client?.setScreen(OverlayEditorScreen())
            }
                .dimensions(width - 150 - layout.margin, 5, 150, 20)
                .build()
        )
    }

    private fun updateListVisibility() {
        featureList?.visible = (currentTab == Tab.FEATURES)
        overlayList?.visible = (currentTab == Tab.OVERLAYS)
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


    override fun renderPanels(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Title at top center
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, NlibTheme.TEXT_PRIMARY)

        // Tab indicator (underline for selected tab)
        val tabY = 25 + 20 + 2  // Below the tab buttons
        val indicatorX = if (currentTab == Tab.FEATURES) width / 2 - 105 else width / 2 + 5
        context.fill(indicatorX, tabY, indicatorX + 100, tabY + 2, NlibTheme.ACCENT)

        // Panel backgrounds using theme colors
        drawPanel(context, leftPanelX, contentY, leftPanelWidth, contentHeight)
        drawPanel(context, rightPanelX, contentY, rightPanelWidth, contentHeight)

        // Render the appropriate list
        when (currentTab) {
            Tab.FEATURES -> featureList?.render(context, mouseX, mouseY, delta)
            Tab.OVERLAYS -> overlayList?.render(context, mouseX, mouseY, delta)
        }
    }

    override fun renderOverlays(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Draw panel headers on top of content
        val leftTitle = if (currentTab == Tab.FEATURES) "Features" else "Overlays"
        context.drawText(textRenderer, leftTitle, leftPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)

        val rightTitle = when (currentTab) {
            Tab.FEATURES -> selectedFeature?.displayName ?: "Select a feature"
            Tab.OVERLAYS -> selectedOverlay?.displayName ?: "Select an overlay"
        }
        context.drawText(textRenderer, rightTitle, rightPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)
    }

    override fun close() {
        CivutilsMod.configManager.saveAll()
        toastManager.success("Configuration saved")
        super.close()
    }

    // === List Widgets ===

    private inner class FeatureListWidget(
        client: net.minecraft.client.MinecraftClient,
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
        override fun render(
            context: DrawContext, index: Int, y: Int, x: Int,
            entryWidth: Int, entryHeight: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float
        ) {
            val selected = (featureList?.selectedOrNull === this)
            renderBackground(context, x, y, entryWidth, entryHeight, hovered, selected)

            val statusColor = if (feature.enabled) Colors.ENABLED else Colors.DISABLED
            context.fill(x + 4, y + 4, x + 8, y + entryHeight - 4, statusColor)

            val font = client!!.textRenderer
            context.drawText(font, feature.displayName, x + 14, y + 6, Colors.TEXT, true)
            context.drawText(font, "§7${feature.category.name.lowercase()}", x + 14, y + 18, Colors.TEXT_SECONDARY, false)
        }
    }

    private inner class OverlayListWidget(
        client: net.minecraft.client.MinecraftClient,
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
        override fun render(
            context: DrawContext, index: Int, y: Int, x: Int,
            entryWidth: Int, entryHeight: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float
        ) {
            val selected = (overlayList?.selectedOrNull === this)
            renderBackground(context, x, y, entryWidth, entryHeight, hovered, selected)

            val statusColor = if (overlay.enabled.value) Colors.ENABLED else Colors.DISABLED
            context.fill(x + 4, y + 4, x + 8, y + entryHeight - 4, statusColor)

            val font = client!!.textRenderer
            context.drawText(font, overlay.displayName, x + 14, y + 6, Colors.TEXT, true)
            val posText = "§7${overlay.position.anchorSection.name.lowercase().replace("_", " ")}"
            context.drawText(font, posText, x + 14, y + 18, Colors.TEXT_SECONDARY, false)
        }
    }
}
