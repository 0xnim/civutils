package xyz.nim.civutils.gui.screens

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.components.EditBox
import net.minecraft.network.chat.Component
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.data.playertag.*
import xyz.nim.civutils.gui.widgets.Colors
import xyz.nim.civutils.models.PlayerTagModel
import xyz.nim.civutils.utils.PlayerHeadRenderer
import xyz.nim.civutils.utils.PlayerTagStyler
import xyz.nim.lib.ui.DropdownWidget
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.lib.mc121.compat.ColorInput
import net.minecraft.client.input.MouseButtonEvent
import xyz.nim.lib.mc121.compat.NlibListWidget
import xyz.nim.civutils.utils.renderOutline

/**
 * Screen for managing player tags and attribute types.
 * Features:
 * - Search/filter for players
 * - Nearby players quick-tag section
 * - Player heads displayed in list
 * - Notes editing
 * - Color picker for attribute values
 * - Live preview of tag appearance
 */
class PlayerTagScreen : CivutilsScreen(Component.literal("Player Tags")) {

    private enum class Tab {
        PLAYERS,
        NEARBY,
        TYPES
    }

    private var currentTab = Tab.PLAYERS
    private var selectedPlayer: TaggedPlayer? = null
    private var selectedType: AttributeType? = null
    private var editingValue: AttributeValue? = null

    // Search
    private var searchBox: EditBox? = null
    private var searchQuery = ""

    // UI regions
    private var leftPanelX = 0
    private var leftPanelWidth = 0
    private var rightPanelX = 0
    private var rightPanelWidth = 0
    private var contentY = 0
    private var contentHeight = 0

    // Lists
    private var playerList: PlayerListWidget? = null
    private var nearbyList: NearbyListWidget? = null
    private var typeList: TypeListWidget? = null
    private var addDefaultsButton: Button? = null

    // Right panel widgets
    private val rightPanelWidgets = mutableListOf<net.minecraft.client.gui.components.AbstractWidget>()
    private val dropdowns = mutableListOf<DropdownWidget>()

    // Text inputs for editing
    private var notesField: EditBox? = null
    private var newTypeIdField: EditBox? = null
    private var newTypeNameField: EditBox? = null
    private var newValueIdField: EditBox? = null
    private var newValueNameField: EditBox? = null
    private var newValuePrefixField: EditBox? = null
    private var colorInput: ColorInput? = null
    private var editColorInput: ColorInput? = null

    // Confirmation dialog state
    private var confirmDialog: ConfirmDialog? = null

    override fun init() {
        super.init()

        // Layout
        val headerAreaHeight = 50
        leftPanelWidth = (width * 0.4).toInt()
        leftPanelX = layout.margin
        rightPanelX = leftPanelX + leftPanelWidth + layout.spacing
        rightPanelWidth = width - rightPanelX - layout.margin
        contentY = headerAreaHeight
        contentHeight = height - contentY - layout.margin

        // Tab buttons
        val tabWidth = 90
        val tabY = 25
        addRenderableWidget(
            Button.builder(Component.literal("Tagged")) {
                switchTab(Tab.PLAYERS)
            }
                .bounds(width / 2 - tabWidth - tabWidth / 2 - 10, tabY, tabWidth, 20)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Nearby")) {
                switchTab(Tab.NEARBY)
            }
                .bounds(width / 2 - tabWidth / 2, tabY, tabWidth, 20)
                .build()
        )

        addRenderableWidget(
            Button.builder(Component.literal("Types")) {
                switchTab(Tab.TYPES)
            }
                .bounds(width / 2 + tabWidth / 2 + 10, tabY, tabWidth, 20)
                .build()
        )

        // Search box (for Players tab) - positioned below panel header
        val searchY = contentY + 22
        searchBox = EditBox(font, leftPanelX + 4, searchY, leftPanelWidth - 8, 16, Component.literal(""))
        searchBox?.setHint(Component.literal("Search players..."))
        searchBox?.setResponder { query ->
            searchQuery = query
            refreshLists()
        }
        searchBox?.let { addWidget(it) }

        // Lists - player list has extra offset for search box
        val listHeaderOffset = 22  // Space for panel title
        val searchBoxOffset = 22   // Extra space for search box on Players tab
        val playerListY = contentY + listHeaderOffset + searchBoxOffset
        val otherListY = contentY + listHeaderOffset
        val playerListHeight = contentHeight - listHeaderOffset - searchBoxOffset - 5
        val otherListHeight = contentHeight - listHeaderOffset - 30

        playerList = PlayerListWidget(minecraft!!, leftPanelWidth, playerListHeight, playerListY, 36) { player ->
            selectedPlayer = player
            editingValue = null
            rebuildRightPanel()
        }
        playerList?.setX(leftPanelX)
        playerList?.let { addWidget(it) }

        nearbyList = NearbyListWidget(minecraft!!, leftPanelWidth, otherListHeight, otherListY, 36) { name, uuid ->
            // Quick tag nearby player
            openQuickTagForPlayer(name, uuid)
        }
        nearbyList?.setX(leftPanelX)
        nearbyList?.let { addWidget(it) }

        typeList = TypeListWidget(minecraft!!, leftPanelWidth, otherListHeight, otherListY, 40) { type ->
            selectedType = type
            editingValue = null
            rebuildRightPanel()
        }
        typeList?.setX(leftPanelX)
        typeList?.let { addWidget(it) }

        // Add Defaults button (bottom of left panel, only on Types tab)
        addDefaultsButton = Button.builder(Component.literal("Add Default Types")) {
            PlayerTagModel.addDefaultAttributeTypes()
            refreshLists()
            toastManager.success("Added default attribute types")
        }
            .bounds(leftPanelX, contentY + contentHeight - 25, leftPanelWidth, 20)
            .build()
        addRenderableWidget(addDefaultsButton!!)

        updateListVisibility()
        refreshLists()
        rebuildRightPanel()
    }

    private fun switchTab(tab: Tab) {
        currentTab = tab
        selectedPlayer = null
        selectedType = null
        editingValue = null
        updateListVisibility()
        rebuildRightPanel()
        refreshLists()
    }

    private fun updateListVisibility() {
        playerList?.visible = (currentTab == Tab.PLAYERS)
        nearbyList?.visible = (currentTab == Tab.NEARBY)
        typeList?.visible = (currentTab == Tab.TYPES)
        searchBox?.visible = (currentTab == Tab.PLAYERS)
        addDefaultsButton?.visible = (currentTab == Tab.TYPES)
    }

    private fun refreshLists() {
        // Player list with search filtering
        playerList?.clearEntries()
        val allPlayers = PlayerTagModel.getAllPlayers()
            .filter { it.name.isNotEmpty() } // Filter out any invalid entries
        val filtered = if (searchQuery.isBlank()) {
            allPlayers
        } else {
            allPlayers.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        for (player in filtered.sortedBy { it.name.lowercase() }) {
            playerList?.addEntryToList(PlayerEntry(player))
        }

        // Nearby list
        nearbyList?.clearEntries()
        val nearbyPlayers = PlayerTagModel.getNearbyPlayers()
        for ((name, uuid) in nearbyPlayers) {
            val isTagged = PlayerTagModel.getPlayer(name) != null
            nearbyList?.addEntryToList(NearbyEntry(name, uuid, isTagged))
        }

        // Type list
        typeList?.clearEntries()
        for (type in PlayerTagModel.getAttributeTypes()) {
            typeList?.addEntryToList(TypeEntry(type))
        }
    }

    private fun openQuickTagForPlayer(name: String, uuid: String?) {
        minecraft?.setScreen(QuickTagScreen(name, uuid))
    }

    private fun rebuildRightPanel() {
        // Clear old widgets
        rightPanelWidgets.forEach { removeWidget(it) }
        rightPanelWidgets.clear()
        dropdowns.clear()
        notesField = null
        newTypeIdField = null
        newTypeNameField = null
        newValueIdField = null
        newValueNameField = null
        newValuePrefixField = null
        colorInput = null
        editColorInput = null

        val startY = contentY + 30
        val widgetWidth = rightPanelWidth - 20

        when (currentTab) {
            Tab.PLAYERS -> buildPlayerPanel(startY, widgetWidth)
            Tab.NEARBY -> buildNearbyPanel(startY, widgetWidth)
            Tab.TYPES -> buildTypePanel(startY, widgetWidth)
        }
    }

    private fun buildPlayerPanel(startY: Int, widgetWidth: Int) {
        var y = startY
        val mc = Minecraft.getInstance()
        val labelWidth = 80

        selectedPlayer?.let { player ->
            // Show player's attributes as dropdowns
            val types = PlayerTagModel.getAttributeTypes()

            for (type in types) {
                val currentValue = player.getAttribute(type.id)

                // Build dropdown options: "None" + all values
                val options = mutableListOf(DropdownWidget.DropdownOption("", "None"))
                for (value in type.values) {
                    options.add(DropdownWidget.DropdownOption(value.id, "${value.style.prefix} ${value.displayName}".trim()))
                }

                val dropdown = DropdownWidget(
                    mc,
                    rightPanelX + 10 + labelWidth + 5,
                    y,
                    widgetWidth - labelWidth - 5,
                    20,
                    options,
                    { selectedValue ->
                        if (selectedValue.isEmpty()) {
                            PlayerTagModel.removePlayerAttribute(player.name, type.id)
                        } else {
                            PlayerTagModel.setPlayerAttribute(player.name, type.id, selectedValue, player.uuid)
                        }
                        refreshLists()
                        selectedPlayer = PlayerTagModel.getPlayer(player.name)
                    },
                    4
                )

                dropdown.setSelected(currentValue ?: "")
                addRenderableWidget(dropdown.button)
                rightPanelWidgets.add(dropdown.button)
                dropdowns.add(dropdown)

                y += 28
            }

            // Notes section
            y += 15
            notesField = EditBox(font, rightPanelX + 10, y, widgetWidth, 18, Component.literal(""))
            notesField?.setHint(Component.literal("Add notes about this player..."))
            notesField?.value = player.notes
            notesField?.setMaxLength(200)
            notesField?.setResponder { notes ->
                PlayerTagModel.setPlayerNote(player.name, notes, player.uuid)
            }
            addRenderableWidget(notesField!!)
            rightPanelWidgets.add(notesField!!)

            // Remove all tags button
            y += 35
            val removeBtn = Button.builder(Component.literal("Remove All Tags")) {
                showConfirmDialog(
                    "Remove all tags from ${player.name}?",
                    "This action cannot be undone."
                ) {
                    PlayerTagModel.untagPlayer(player.name)
                    selectedPlayer = null
                    refreshLists()
                    rebuildRightPanel()
                }
            }
                .bounds(rightPanelX + 10, y, widgetWidth, 20)
                .build()
            addRenderableWidget(removeBtn)
            rightPanelWidgets.add(removeBtn)
        } ?: run {
            // No player selected - show hint
            // (nothing to show here, the title says "Select a player")
        }
    }

    private fun buildNearbyPanel(startY: Int, widgetWidth: Int) {
        var y = startY
        // Show instructions
        val instructions = listOf(
            "Click a player to quick-tag them",
            "Green dot = already tagged",
            "Use /tag <name> for command"
        )
        // Instructions will be rendered in renderOverlays
    }

    private fun buildTypePanel(startY: Int, widgetWidth: Int) {
        var y = startY

        selectedType?.let { type ->
            if (editingValue != null) {
                // Editing an existing value
                buildValueEditor(y, widgetWidth, type, editingValue!!)
            } else {
                // Show type's values
                for (value in type.values) {
                    val colorHex = String.format("#%06X", value.style.color and 0xFFFFFF)
                    val prefix = if (value.style.prefix.isNotEmpty()) "${value.style.prefix} " else ""

                    // Value button with color indicator
                    val valueBtn = Button.builder(Component.literal("$prefix${value.displayName}")) {
                        // Open editor for this value
                        editingValue = value
                        rebuildRightPanel()
                    }
                        .bounds(rightPanelX + 10, y, widgetWidth - 30, 20)
                        .build()
                    addRenderableWidget(valueBtn)
                    rightPanelWidgets.add(valueBtn)

                    // Delete button
                    val deleteBtn = Button.builder(Component.literal("X")) {
                        showConfirmDialog(
                            "Delete value '${value.displayName}'?",
                            "Players with this value will lose it."
                        ) {
                            PlayerTagModel.removeAttributeValue(type.id, value.id)
                            selectedType = PlayerTagModel.getAttributeType(type.id)
                            refreshLists()
                            rebuildRightPanel()
                        }
                    }
                        .bounds(rightPanelX + widgetWidth - 15, y, 20, 20)
                        .build()
                    addRenderableWidget(deleteBtn)
                    rightPanelWidgets.add(deleteBtn)

                    y += 25
                }

                // Add new value section
                y += 15
                buildNewValueForm(y, widgetWidth, type)
            }
        } ?: run {
            // No type selected - show create new type form
            buildNewTypeForm(y, widgetWidth)
        }
    }

    private fun buildValueEditor(startY: Int, widgetWidth: Int, type: AttributeType, value: AttributeValue) {
        var y = startY

        // Back button
        val backBtn = Button.builder(Component.literal("< Back")) {
            editingValue = null
            rebuildRightPanel()
        }
            .bounds(rightPanelX + 10, y, 60, 20)
            .build()
        addRenderableWidget(backBtn)
        rightPanelWidgets.add(backBtn)
        y += 30

        // Value Name
        newValueNameField = EditBox(font, rightPanelX + 10, y, widgetWidth, 18, Component.literal(""))
        newValueNameField?.setHint(Component.literal("Display Name"))
        newValueNameField?.value = value.displayName
        addRenderableWidget(newValueNameField!!)
        rightPanelWidgets.add(newValueNameField!!)
        y += 23

        // Prefix
        newValuePrefixField = EditBox(font, rightPanelX + 10, y, widgetWidth / 2 - 5, 18, Component.literal(""))
        newValuePrefixField?.setHint(Component.literal("Prefix icon"))
        newValuePrefixField?.value = value.style.prefix
        addRenderableWidget(newValuePrefixField!!)
        rightPanelWidgets.add(newValuePrefixField!!)

        // Color picker
        editColorInput = ColorInput(rightPanelX + 10 + widgetWidth / 2 + 5, y, widgetWidth / 2 - 5, 18, value.style.color) { }
        addRenderableWidget(editColorInput!!)
        rightPanelWidgets.add(editColorInput!!)
        y += 28

        // Save button
        val saveBtn = Button.builder(Component.literal("Save Changes")) {
            val valueName = newValueNameField?.value?.trim() ?: value.displayName
            val prefix = newValuePrefixField?.value?.trim() ?: ""
            val color = editColorInput?.getColor() ?: value.style.color

            val updatedValue = value.copy(
                displayName = valueName,
                style = value.style.copy(color = color, prefix = prefix)
            )

            if (PlayerTagModel.updateAttributeValue(type.id, value.id, updatedValue)) {
                toastManager.success("Updated value '$valueName'")
                editingValue = null
                selectedType = PlayerTagModel.getAttributeType(type.id)
                rebuildRightPanel()
            } else {
                toastManager.error("Failed to update value")
            }
        }
            .bounds(rightPanelX + 10, y, widgetWidth, 20)
            .build()
        addRenderableWidget(saveBtn)
        rightPanelWidgets.add(saveBtn)
    }

    private fun buildNewValueForm(startY: Int, widgetWidth: Int, type: AttributeType) {
        var y = startY

        // Value ID
        newValueIdField = EditBox(font, rightPanelX + 10, y, widgetWidth / 2 - 5, 18, Component.literal(""))
        newValueIdField?.setHint(Component.literal("value_id"))
        addRenderableWidget(newValueIdField!!)
        rightPanelWidgets.add(newValueIdField!!)

        // Value Name
        newValueNameField = EditBox(font, rightPanelX + 10 + widgetWidth / 2 + 5, y, widgetWidth / 2 - 5, 18, Component.literal(""))
        newValueNameField?.setHint(Component.literal("Display Name"))
        addRenderableWidget(newValueNameField!!)
        rightPanelWidgets.add(newValueNameField!!)
        y += 23

        // Prefix
        newValuePrefixField = EditBox(font, rightPanelX + 10, y, widgetWidth / 2 - 5, 18, Component.literal(""))
        newValuePrefixField?.setHint(Component.literal("Prefix icon"))
        addRenderableWidget(newValuePrefixField!!)
        rightPanelWidgets.add(newValuePrefixField!!)

        // Color picker
        colorInput = ColorInput(rightPanelX + 10 + widgetWidth / 2 + 5, y, widgetWidth / 2 - 5, 18, 0xFFFFFF) { }
        addRenderableWidget(colorInput!!)
        rightPanelWidgets.add(colorInput!!)
        y += 23

        // Add value button
        val addValueBtn = Button.builder(Component.literal("Add Value")) {
            val valueId = newValueIdField?.value?.trim() ?: ""
            val valueName = newValueNameField?.value?.trim() ?: ""
            val prefix = newValuePrefixField?.value?.trim() ?: ""
            val color = colorInput?.getColor() ?: 0xFFFFFF

            if (valueId.isNotEmpty() && valueName.isNotEmpty()) {
                val newValue = AttributeValue(
                    id = valueId,
                    displayName = valueName,
                    style = AttributeStyle(color = color, prefix = prefix)
                )
                if (PlayerTagModel.addAttributeValue(type.id, newValue)) {
                    selectedType = PlayerTagModel.getAttributeType(type.id)
                    refreshLists()
                    rebuildRightPanel()
                    toastManager.success("Added value '$valueName'")
                } else {
                    toastManager.error("Value ID already exists")
                }
            }
        }
            .bounds(rightPanelX + 10, y, widgetWidth, 20)
            .build()
        addRenderableWidget(addValueBtn)
        rightPanelWidgets.add(addValueBtn)
        y += 30

        // Delete type button
        val deleteBtn = Button.builder(Component.literal("Delete Type")) {
            val usageCount = PlayerTagModel.getPlayersWithAttribute(type.id).size
            showConfirmDialog(
                "Delete type '${type.displayName}'?",
                if (usageCount > 0) "$usageCount players will lose this attribute." else "This action cannot be undone."
            ) {
                PlayerTagModel.deleteAttributeType(type.id)
                selectedType = null
                refreshLists()
                rebuildRightPanel()
            }
        }
            .bounds(rightPanelX + 10, y, widgetWidth, 20)
            .build()
        addRenderableWidget(deleteBtn)
        rightPanelWidgets.add(deleteBtn)
    }

    private fun buildNewTypeForm(startY: Int, widgetWidth: Int) {
        var y = startY + 10

        // Type ID
        newTypeIdField = EditBox(font, rightPanelX + 10, y, widgetWidth, 18, Component.literal(""))
        newTypeIdField?.setHint(Component.literal("type_id (e.g., trust)"))
        addRenderableWidget(newTypeIdField!!)
        rightPanelWidgets.add(newTypeIdField!!)
        y += 23

        // Type Name
        newTypeNameField = EditBox(font, rightPanelX + 10, y, widgetWidth, 18, Component.literal(""))
        newTypeNameField?.setHint(Component.literal("Display Name (e.g., Trust Level)"))
        addRenderableWidget(newTypeNameField!!)
        rightPanelWidgets.add(newTypeNameField!!)
        y += 28

        // Create button
        val createBtn = Button.builder(Component.literal("Create Type")) {
            val typeId = newTypeIdField?.value?.trim() ?: ""
            val typeName = newTypeNameField?.value?.trim() ?: ""

            if (typeId.isNotEmpty() && typeName.isNotEmpty()) {
                if (PlayerTagModel.createAttributeType(typeId, typeName)) {
                    refreshLists()
                    rebuildRightPanel()
                    toastManager.success("Created type '$typeName'")
                } else {
                    toastManager.error("Type ID already exists")
                }
            }
        }
            .bounds(rightPanelX + 10, y, widgetWidth, 20)
            .build()
        addRenderableWidget(createBtn)
        rightPanelWidgets.add(createBtn)
    }

    private fun showConfirmDialog(title: String, message: String, onConfirm: () -> Unit) {
        confirmDialog = ConfirmDialog(title, message, onConfirm) {
            confirmDialog = null
        }
    }

    override fun renderPanels(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {
        // Title
        guiGraphics.drawCenteredString(font, title, width / 2, 8, NlibTheme.TEXT_PRIMARY)

        // Tab indicator
        val tabWidth = 90
        val tabY = 25 + 20 + 2
        val indicatorX = when (currentTab) {
            Tab.PLAYERS -> width / 2 - tabWidth - tabWidth / 2 - 10
            Tab.NEARBY -> width / 2 - tabWidth / 2
            Tab.TYPES -> width / 2 + tabWidth / 2 + 10
        }
        guiGraphics.fill(indicatorX, tabY, indicatorX + tabWidth, tabY + 2, NlibTheme.ACCENT)

        // Panels
        drawPanel(guiGraphics, leftPanelX, contentY, leftPanelWidth, contentHeight)
        drawPanel(guiGraphics, rightPanelX, contentY, rightPanelWidth, contentHeight)

        // Search box background
        if (currentTab == Tab.PLAYERS) {
            searchBox?.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        // Lists
        when (currentTab) {
            Tab.PLAYERS -> playerList?.render(guiGraphics, mouseX, mouseY, partialTick)
            Tab.NEARBY -> nearbyList?.render(guiGraphics, mouseX, mouseY, partialTick)
            Tab.TYPES -> typeList?.render(guiGraphics, mouseX, mouseY, partialTick)
        }
    }

    override fun renderOverlays(guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, partialTick: Float) {

        val leftTitle = when (currentTab) {
            Tab.PLAYERS -> "Tagged Players (${PlayerTagModel.getAllPlayers().size})"
            Tab.NEARBY -> "Nearby Players"
            Tab.TYPES -> "Attribute Types"
        }
        guiGraphics.drawString(font, leftTitle, leftPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)

        val rightTitle = when (currentTab) {
            Tab.PLAYERS -> selectedPlayer?.name ?: "Select a player"
            Tab.NEARBY -> "Click to quick-tag"
            Tab.TYPES -> if (editingValue != null) "Edit Value" else (selectedType?.displayName ?: "Create new type")
        }
        guiGraphics.drawString(font, rightTitle, rightPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)

        // Draw attribute type labels next to dropdowns
        if (currentTab == Tab.PLAYERS && selectedPlayer != null) {
            val types = PlayerTagModel.getAttributeTypes()
            var y = contentY + 30
            for (type in types) {
                guiGraphics.drawString(font, type.displayName, rightPanelX + 10, y + 6, NlibTheme.TEXT_PRIMARY, false)
                y += 28
            }

            // Notes label
            y += 15
            guiGraphics.drawString(font, "Notes:", rightPanelX + 10, y - 12, NlibTheme.TEXT_SECONDARY, false)

            // Live preview of tag appearance (positioned after the Remove All Tags button)
            // Button is at y + 35 with height 20, so preview starts at y + 35 + 20 + 10 margin = y + 65
            selectedPlayer?.let { player ->
                y += 65
                guiGraphics.drawString(font, "Preview:", rightPanelX + 10, y, NlibTheme.TEXT_SECONDARY, false)
                y += 14
                // Draw the player name with styling applied
                val styledName = PlayerTagStyler.getStyledComponent(player.name)
                guiGraphics.drawString(font, styledName, rightPanelX + 10, y, NlibTheme.TEXT_PRIMARY, true)
                // Also draw plain name below for reference
                y += 12
                guiGraphics.drawString(font, player.name, rightPanelX + 10, y, NlibTheme.TEXT_SECONDARY, false)
            }
        }

        // Draw color indicators for type values
        if (currentTab == Tab.TYPES && selectedType != null && editingValue == null) {
            var y = contentY + 30
            for (value in selectedType!!.values) {
                val colorWithAlpha = value.style.color or (0xFF shl 24)
                guiGraphics.fill(rightPanelX + 5, y + 4, rightPanelX + 9, y + 16, colorWithAlpha)
                y += 25
            }
        }

        // Render dropdown overlays on top of everything
        for (dropdown in dropdowns) {
            dropdown.render(guiGraphics, mouseX, mouseY, partialTick)
        }

        // Confirmation dialog
        confirmDialog?.render(guiGraphics, font, width, height, mouseX, mouseY)
    }

    override fun mouseClicked(event: MouseButtonEvent, consumed: Boolean): Boolean {
        val mouseX = event.x()
        val mouseY = event.y()
        val button = event.button()

        // Handle confirmation dialog first
        confirmDialog?.let { dialog ->
            if (dialog.mouseClicked(mouseX, mouseY, button)) return true
        }

        // Handle dropdown clicks
        for (dropdown in dropdowns) {
            if (dropdown.mouseClicked(mouseX, mouseY, button)) return true
        }
        return super.mouseClicked(event, consumed)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        for (dropdown in dropdowns) {
            if (dropdown.mouseScrolled(mouseX, mouseY, verticalAmount)) return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun onClose() {
        PlayerTagModel.save()
        super.onClose()
    }

    // === List Widgets ===

    private inner class PlayerListWidget(
        client: Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int,
        private val onSelect: (TaggedPlayer) -> Unit
    ) : NlibListWidget<PlayerEntry>(client, width, height, y, itemHeight) {

        override fun setSelected(entry: PlayerEntry?) {
            super.setSelected(entry)
            entry?.player?.let { onSelect(it) }
        }
    }

    private inner class PlayerEntry(val player: TaggedPlayer) : NlibListWidget.Entry<PlayerEntry>() {
        override fun renderContent(
            guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float
        ) {
            val x = getX()
            val y = getY()
            val entryWidth = getWidth()
            val entryHeight = getHeight()
            val selected = (playerList?.selected === this)
            renderBackground(guiGraphics, x, y, entryWidth, entryHeight, hovered, selected)

            // Player head with border color (centered vertically)
            val headSize = 24
            val headY = y + (entryHeight - headSize) / 2
            val primaryColor = PlayerTagStyler.getPrimaryColor(player.name)
            PlayerHeadRenderer.renderHeadWithBorder(
                guiGraphics, player.name, player.uuid,
                x + 6, headY, headSize, primaryColor
            )

            // Player name
            val textX = x + 38
            // Defensive: handle null/empty name (can happen with corrupted JSON data)
            val rawName = try { player.name } catch (e: Exception) { "" }
            val displayName = if (rawName.isNotBlank()) rawName else "[No Name]"
            guiGraphics.drawString(Minecraft.getInstance().font, displayName, textX, y + 8, 0xFFFFFF, true)

            // Show attributes below name
            val attrs = player.attributes.entries.map { (typeId, valueId) ->
                val type = PlayerTagModel.getAttributeType(typeId)
                val value = type?.getValue(valueId)
                if (value != null) {
                    "${value.style.prefix} ${value.displayName}".trim()
                } else {
                    // Fallback: show raw value if type/value not found
                    valueId
                }
            }.joinToString(", ")

            if (attrs.isNotEmpty()) {
                guiGraphics.drawString(Minecraft.getInstance().font, attrs, textX, y + 20, Colors.TEXT_SECONDARY, false)
            }
        }
    }

    private inner class NearbyListWidget(
        client: Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int,
        private val onSelect: (String, String?) -> Unit
    ) : NlibListWidget<NearbyEntry>(client, width, height, y, itemHeight) {

        override fun setSelected(entry: NearbyEntry?) {
            super.setSelected(entry)
            entry?.let { onSelect(it.name, it.uuid) }
        }
    }

    private inner class NearbyEntry(val name: String, val uuid: String?, val isTagged: Boolean) : NlibListWidget.Entry<NearbyEntry>() {
        override fun renderContent(
            guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float
        ) {
            val x = getX()
            val y = getY()
            val entryWidth = getWidth()
            val entryHeight = getHeight()
            val selected = (nearbyList?.selected === this)
            renderBackground(guiGraphics, x, y, entryWidth, entryHeight, hovered, selected)

            // Player head (centered vertically)
            val headSize = 24
            val headY = y + (entryHeight - headSize) / 2
            val primaryColor = if (isTagged) PlayerTagStyler.getPrimaryColor(name) else null
            PlayerHeadRenderer.renderHeadWithBorder(
                guiGraphics, name, uuid,
                x + 6, headY, headSize, primaryColor
            )

            // Tagged indicator
            if (isTagged) {
                guiGraphics.fill(x + entryWidth - 8, y + 6, x + entryWidth - 4, y + entryHeight - 6, Colors.ENABLED)
            }

            // Player name
            val textX = x + 38
            val nameColor = primaryColor ?: Colors.TEXT
            guiGraphics.drawString(Minecraft.getInstance().font, name, textX, y + 8, nameColor, true)

            val subText = if (isTagged) "Tagged" else "Click to tag"
            guiGraphics.drawString(Minecraft.getInstance().font, subText, textX, y + 20, Colors.TEXT_SECONDARY, false)
        }
    }

    private inner class TypeListWidget(
        client: Minecraft,
        width: Int,
        height: Int,
        y: Int,
        itemHeight: Int,
        private val onSelect: (AttributeType) -> Unit
    ) : NlibListWidget<TypeEntry>(client, width, height, y, itemHeight) {

        override fun setSelected(entry: TypeEntry?) {
            super.setSelected(entry)
            entry?.type?.let { onSelect(it) }
        }
    }

    private inner class TypeEntry(val type: AttributeType) : NlibListWidget.Entry<TypeEntry>() {
        override fun renderContent(
            guiGraphics: GuiGraphics, mouseX: Int, mouseY: Int, hovered: Boolean, delta: Float
        ) {
            val x = getX()
            val y = getY()
            val entryWidth = getWidth()
            val entryHeight = getHeight()
            val selected = (typeList?.selected === this)
            renderBackground(guiGraphics, x, y, entryWidth, entryHeight, hovered, selected)

            guiGraphics.drawString(Minecraft.getInstance().font, type.displayName, x + 10, y + 10, Colors.TEXT, true)

            val valueCount = type.values.size
            val usageCount = PlayerTagModel.getPlayersWithAttribute(type.id).size
            guiGraphics.drawString(Minecraft.getInstance().font, "$valueCount values, $usageCount players", x + 10, y + 24, Colors.TEXT_SECONDARY, false)
        }
    }

    // === Confirmation Dialog ===

    private class ConfirmDialog(
        private val title: String,
        private val message: String,
        private val onConfirm: () -> Unit,
        private val onCancel: () -> Unit
    ) {
        private val dialogWidth = 250
        private val dialogHeight = 100

        fun render(guiGraphics: GuiGraphics, font: net.minecraft.client.gui.Font, screenWidth: Int, screenHeight: Int, mouseX: Int, mouseY: Int) {
            val x = (screenWidth - dialogWidth) / 2
            val y = (screenHeight - dialogHeight) / 2

            // Darken background
            guiGraphics.fill(0, 0, screenWidth, screenHeight, 0x80000000.toInt())

            // Dialog background
            guiGraphics.fill(x, y, x + dialogWidth, y + dialogHeight, NlibTheme.PANEL_BG)
            guiGraphics.renderOutline(x, y, dialogWidth, dialogHeight, NlibTheme.PANEL_BORDER)

            // Title
            guiGraphics.drawCenteredString(font, title, screenWidth / 2, y + 10, NlibTheme.TEXT_PRIMARY)

            // Message
            guiGraphics.drawCenteredString(font, message, screenWidth / 2, y + 30, NlibTheme.TEXT_SECONDARY)

            // Buttons
            val btnWidth = 80
            val btnHeight = 20
            val cancelX = x + dialogWidth / 2 - btnWidth - 10
            val confirmX = x + dialogWidth / 2 + 10
            val btnY = y + dialogHeight - btnHeight - 15

            // Cancel button
            val cancelHovered = mouseX >= cancelX && mouseX < cancelX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight
            guiGraphics.fill(cancelX, btnY, cancelX + btnWidth, btnY + btnHeight, if (cancelHovered) NlibTheme.BACKGROUND_HOVER else NlibTheme.BACKGROUND_LIGHT)
            guiGraphics.renderOutline(cancelX, btnY, btnWidth, btnHeight, NlibTheme.PANEL_BORDER)
            guiGraphics.drawCenteredString(font, "Cancel", cancelX + btnWidth / 2, btnY + 6, NlibTheme.TEXT_PRIMARY)

            // Confirm button (red)
            val confirmHovered = mouseX >= confirmX && mouseX < confirmX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight
            guiGraphics.fill(confirmX, btnY, confirmX + btnWidth, btnY + btnHeight, if (confirmHovered) 0xFF661111.toInt() else 0xFF441111.toInt())
            guiGraphics.renderOutline(confirmX, btnY, btnWidth, btnHeight, 0xFFFF5555.toInt())
            guiGraphics.drawCenteredString(font, "Delete", confirmX + btnWidth / 2, btnY + 6, 0xFFFF5555.toInt())
        }

        fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
            if (button != 0) return false

            val screenWidth = Minecraft.getInstance().window.guiScaledWidth
            val screenHeight = Minecraft.getInstance().window.guiScaledHeight
            val x = (screenWidth - dialogWidth) / 2
            val y = (screenHeight - dialogHeight) / 2

            val btnWidth = 80
            val btnHeight = 20
            val cancelX = x + dialogWidth / 2 - btnWidth - 10
            val confirmX = x + dialogWidth / 2 + 10
            val btnY = y + dialogHeight - btnHeight - 15

            if (mouseX >= cancelX && mouseX < cancelX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight) {
                onCancel()
                return true
            }

            if (mouseX >= confirmX && mouseX < confirmX + btnWidth && mouseY >= btnY && mouseY < btnY + btnHeight) {
                onConfirm()
                onCancel()
                return true
            }

            return true // Block clicks outside dialog
        }
    }
}
