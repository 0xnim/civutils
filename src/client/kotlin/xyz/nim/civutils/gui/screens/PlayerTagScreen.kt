package xyz.nim.civutils.gui.screens

import net.minecraft.client.MinecraftClient
import net.minecraft.client.gui.DrawContext
import net.minecraft.client.gui.widget.ButtonWidget
import net.minecraft.client.gui.widget.TextFieldWidget
import net.minecraft.text.Text
import xyz.nim.civutils.core.CivutilsMod
import xyz.nim.civutils.data.playertag.*
import xyz.nim.lib.ui.DropdownWidget
import xyz.nim.lib.ui.NlibTheme
import xyz.nim.lib.ui.components.NlibListWidget
import xyz.nim.civutils.gui.widgets.Colors
import xyz.nim.civutils.models.PlayerTagModel

/**
 * Screen for managing player tags and attribute types.
 */
class PlayerTagScreen : CivutilsScreen(Text.literal("Player Tags")) {

    private enum class Tab {
        PLAYERS,
        TYPES
    }

    private var currentTab = Tab.PLAYERS
    private var selectedPlayer: TaggedPlayer? = null
    private var selectedType: AttributeType? = null

    // UI regions
    private var leftPanelX = 0
    private var leftPanelWidth = 0
    private var rightPanelX = 0
    private var rightPanelWidth = 0
    private var contentY = 0
    private var contentHeight = 0

    // Lists
    private var playerList: PlayerListWidget? = null
    private var typeList: TypeListWidget? = null

    // Right panel widgets
    private val rightPanelWidgets = mutableListOf<net.minecraft.client.gui.widget.ClickableWidget>()
    private val dropdowns = mutableListOf<DropdownWidget>()

    // Text input for new type creation
    private var newTypeIdField: TextFieldWidget? = null
    private var newTypeNameField: TextFieldWidget? = null

    // Text input for new value creation
    private var newValueIdField: TextFieldWidget? = null
    private var newValueNameField: TextFieldWidget? = null
    private var newValueColorField: TextFieldWidget? = null
    private var newValuePrefixField: TextFieldWidget? = null

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
        val tabWidth = 120
        val tabY = 25
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Tagged Players")) {
                currentTab = Tab.PLAYERS
                selectedPlayer = null
                selectedType = null
                updateListVisibility()
                rebuildRightPanel()
                refreshLists()
            }
                .dimensions(width / 2 - tabWidth - 5, tabY, tabWidth, 20)
                .build()
        )

        addDrawableChild(
            ButtonWidget.builder(Text.literal("Attribute Types")) {
                currentTab = Tab.TYPES
                selectedPlayer = null
                selectedType = null
                updateListVisibility()
                rebuildRightPanel()
                refreshLists()
            }
                .dimensions(width / 2 + 5, tabY, tabWidth, 20)
                .build()
        )

        // Player list
        val listHeaderOffset = 25
        val listY = contentY + listHeaderOffset
        val listHeight = contentHeight - listHeaderOffset - 30

        playerList = PlayerListWidget(client!!, leftPanelWidth, listHeight, listY, 40) { player ->
            selectedPlayer = player
            rebuildRightPanel()
        }
        playerList?.setX(leftPanelX)
        addSelectableChild(playerList)

        // Type list
        typeList = TypeListWidget(client!!, leftPanelWidth, listHeight, listY, 36) { type ->
            selectedType = type
            rebuildRightPanel()
        }
        typeList?.setX(leftPanelX)
        addSelectableChild(typeList)

        // Set initial visibility
        updateListVisibility()

        // Add Defaults button (bottom of left panel)
        addDrawableChild(
            ButtonWidget.builder(Text.literal("Add Default Types")) {
                PlayerTagModel.addDefaultAttributeTypes()
                refreshLists()
                toastManager.success("Added default attribute types")
            }
                .dimensions(leftPanelX, contentY + contentHeight - 25, leftPanelWidth, 20)
                .build()
        )

        refreshLists()
        rebuildRightPanel()
    }

    private fun updateListVisibility() {
        playerList?.visible = (currentTab == Tab.PLAYERS)
        typeList?.visible = (currentTab == Tab.TYPES)
    }

    private fun refreshLists() {
        playerList?.clearEntries()
        for (player in PlayerTagModel.getAllPlayers()) {
            playerList?.addEntryToList(PlayerEntry(player))
        }

        typeList?.clearEntries()
        for (type in PlayerTagModel.getAttributeTypes()) {
            typeList?.addEntryToList(TypeEntry(type))
        }
    }

    private fun rebuildRightPanel() {
        // Clear old widgets
        rightPanelWidgets.forEach { remove(it) }
        rightPanelWidgets.clear()
        dropdowns.clear()
        newTypeIdField = null
        newTypeNameField = null
        newValueIdField = null
        newValueNameField = null
        newValueColorField = null
        newValuePrefixField = null

        val startY = contentY + 30
        val widgetWidth = rightPanelWidth - 20
        var y = startY

        when (currentTab) {
            Tab.PLAYERS -> buildPlayerPanel(y, widgetWidth)
            Tab.TYPES -> buildTypePanel(y, widgetWidth)
        }
    }

    private fun buildPlayerPanel(startY: Int, widgetWidth: Int) {
        var y = startY
        val mc = MinecraftClient.getInstance()
        val labelWidth = 80

        selectedPlayer?.let { player ->
            // Show player's attributes as dropdowns
            val types = PlayerTagModel.getAttributeTypes()

            for (type in types) {
                val currentValue = player.getAttribute(type.id)

                // Build dropdown options: "None" + all values
                val options = mutableListOf(DropdownWidget.DropdownOption("", "None"))
                for (value in type.values) {
                    options.add(DropdownWidget.DropdownOption(value.id, value.displayName))
                }

                // Create dropdown
                val dropdown = DropdownWidget(
                    mc,
                    rightPanelX + 10 + labelWidth + 5,
                    y,
                    widgetWidth - labelWidth - 5,
                    20,
                    options,
                    { selectedValue ->
                        if (selectedValue.isEmpty()) {
                            PlayerTagModel.removePlayerAttribute(player.uuid, type.id)
                        } else {
                            PlayerTagModel.setPlayerAttribute(player.uuid, player.lastKnownName, type.id, selectedValue)
                        }
                        refreshLists()
                        selectedPlayer = PlayerTagModel.getPlayer(player.uuid)
                    },
                    4
                )

                // Set current selection
                dropdown.setSelected(currentValue ?: "")

                // Add the button to the screen
                addDrawableChild(dropdown.button)
                rightPanelWidgets.add(dropdown.button)
                dropdowns.add(dropdown)

                y += 28
            }

            // Remove all tags button
            y += 15
            val removeBtn = ButtonWidget.builder(Text.literal("Remove All Tags")) {
                confirm("Remove Tags", "Remove all tags from ${player.lastKnownName}?") {
                    PlayerTagModel.untagPlayer(player.uuid)
                    selectedPlayer = null
                    refreshLists()
                    rebuildRightPanel()
                }
            }
                .dimensions(rightPanelX + 10, y, widgetWidth, 20)
                .build()
            addDrawableChild(removeBtn)
            rightPanelWidgets.add(removeBtn)
        } ?: run {
            // No player selected - show online players to tag
            val onlinePlayers = mc.networkHandler?.playerList?.take(10) ?: emptyList()

            if (onlinePlayers.isNotEmpty() && PlayerTagModel.getAttributeTypes().isNotEmpty()) {
                y += 25
                for (entry in onlinePlayers) {
                    val name = entry.profile.name
                    val isTagged = PlayerTagModel.getPlayerByName(name) != null

                    if (!isTagged) {
                        val btn = ButtonWidget.builder(Text.literal("+ Tag $name")) {
                            // Quick tag with first type's first value
                            val type = PlayerTagModel.getAttributeTypes().firstOrNull()
                            val value = type?.values?.firstOrNull()
                            if (type != null && value != null) {
                                val uuid = entry.profile.id.toString()
                                PlayerTagModel.setPlayerAttribute(uuid, name, type.id, value.id)
                                refreshLists()
                                selectedPlayer = PlayerTagModel.getPlayer(uuid)
                                rebuildRightPanel()
                            }
                        }
                            .dimensions(rightPanelX + 10, y, widgetWidth, 20)
                            .build()
                        addDrawableChild(btn)
                        rightPanelWidgets.add(btn)
                        y += 25
                    }
                }
            }
        }
    }

    private fun buildTypePanel(startY: Int, widgetWidth: Int) {
        var y = startY

        selectedType?.let { type ->
            // Show type's values
            for (value in type.values) {
                val colorHex = String.format("#%06X", value.style.color and 0xFFFFFF)
                val prefix = if (value.style.prefix.isNotEmpty()) "${value.style.prefix} " else ""

                val btn = ButtonWidget.builder(
                    Text.literal("$prefix${value.displayName} ($colorHex)")
                ) {
                    // Delete value
                    confirm("Delete Value", "Delete '${value.displayName}' from ${type.displayName}?") {
                        PlayerTagModel.removeAttributeValue(type.id, value.id)
                        selectedType = PlayerTagModel.getAttributeType(type.id)
                        refreshLists()
                        rebuildRightPanel()
                    }
                }
                    .dimensions(rightPanelX + 10, y, widgetWidth, 20)
                    .build()
                addDrawableChild(btn)
                rightPanelWidgets.add(btn)
                y += 25
            }

            // Add new value section
            y += 15

            // Value ID
            newValueIdField = TextFieldWidget(textRenderer, rightPanelX + 10, y, widgetWidth / 2 - 5, 18, Text.literal(""))
            newValueIdField?.setPlaceholder(Text.literal("value_id"))
            addDrawableChild(newValueIdField!!)
            rightPanelWidgets.add(newValueIdField!!)

            // Value Name
            newValueNameField = TextFieldWidget(textRenderer, rightPanelX + 10 + widgetWidth / 2 + 5, y, widgetWidth / 2 - 5, 18, Text.literal(""))
            newValueNameField?.setPlaceholder(Text.literal("Display Name"))
            addDrawableChild(newValueNameField!!)
            rightPanelWidgets.add(newValueNameField!!)

            y += 23

            // Color
            newValueColorField = TextFieldWidget(textRenderer, rightPanelX + 10, y, widgetWidth / 2 - 5, 18, Text.literal(""))
            newValueColorField?.setPlaceholder(Text.literal("Color (FF5555)"))
            addDrawableChild(newValueColorField!!)
            rightPanelWidgets.add(newValueColorField!!)

            // Prefix
            newValuePrefixField = TextFieldWidget(textRenderer, rightPanelX + 10 + widgetWidth / 2 + 5, y, widgetWidth / 2 - 5, 18, Text.literal(""))
            newValuePrefixField?.setPlaceholder(Text.literal("Prefix icon"))
            addDrawableChild(newValuePrefixField!!)
            rightPanelWidgets.add(newValuePrefixField!!)

            y += 23

            // Add value button
            val addValueBtn = ButtonWidget.builder(Text.literal("Add Value")) {
                val valueId = newValueIdField?.text?.trim() ?: ""
                val valueName = newValueNameField?.text?.trim() ?: ""
                val colorStr = newValueColorField?.text?.trim() ?: "FFFFFF"
                val prefix = newValuePrefixField?.text?.trim() ?: ""

                if (valueId.isNotEmpty() && valueName.isNotEmpty()) {
                    val color = parseColor(colorStr) ?: 0xFFFFFF
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
                .dimensions(rightPanelX + 10, y, widgetWidth, 20)
                .build()
            addDrawableChild(addValueBtn)
            rightPanelWidgets.add(addValueBtn)

            y += 30

            // Delete type button
            val deleteBtn = ButtonWidget.builder(Text.literal("Delete Type")) {
                confirm("Delete Type", "Delete '${type.displayName}' and all its values?") {
                    PlayerTagModel.deleteAttributeType(type.id)
                    selectedType = null
                    refreshLists()
                    rebuildRightPanel()
                }
            }
                .dimensions(rightPanelX + 10, y, widgetWidth, 20)
                .build()
            addDrawableChild(deleteBtn)
            rightPanelWidgets.add(deleteBtn)

        } ?: run {
            // No type selected - show create new type form
            y += 10

            // Type ID
            newTypeIdField = TextFieldWidget(textRenderer, rightPanelX + 10, y, widgetWidth, 18, Text.literal(""))
            newTypeIdField?.setPlaceholder(Text.literal("type_id (e.g., trust)"))
            addDrawableChild(newTypeIdField!!)
            rightPanelWidgets.add(newTypeIdField!!)

            y += 23

            // Type Name
            newTypeNameField = TextFieldWidget(textRenderer, rightPanelX + 10, y, widgetWidth, 18, Text.literal(""))
            newTypeNameField?.setPlaceholder(Text.literal("Display Name (e.g., Trust Level)"))
            addDrawableChild(newTypeNameField!!)
            rightPanelWidgets.add(newTypeNameField!!)

            y += 28

            // Create button
            val createBtn = ButtonWidget.builder(Text.literal("Create Type")) {
                val typeId = newTypeIdField?.text?.trim() ?: ""
                val typeName = newTypeNameField?.text?.trim() ?: ""

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
                .dimensions(rightPanelX + 10, y, widgetWidth, 20)
                .build()
            addDrawableChild(createBtn)
            rightPanelWidgets.add(createBtn)
        }
    }


    override fun renderPanels(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 8, NlibTheme.TEXT_PRIMARY)

        // Tab indicator
        val tabY = 25 + 20 + 2
        val indicatorX = if (currentTab == Tab.PLAYERS) width / 2 - 125 else width / 2 + 5
        context.fill(indicatorX, tabY, indicatorX + 120, tabY + 2, NlibTheme.ACCENT)

        // Panels
        drawPanel(context, leftPanelX, contentY, leftPanelWidth, contentHeight)
        drawPanel(context, rightPanelX, contentY, rightPanelWidth, contentHeight)

        // Lists
        when (currentTab) {
            Tab.PLAYERS -> playerList?.render(context, mouseX, mouseY, delta)
            Tab.TYPES -> typeList?.render(context, mouseX, mouseY, delta)
        }
    }

    override fun renderOverlays(context: DrawContext, mouseX: Int, mouseY: Int, delta: Float) {
        val leftTitle = if (currentTab == Tab.PLAYERS) "Tagged Players" else "Attribute Types"
        context.drawText(textRenderer, leftTitle, leftPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)

        val rightTitle = when (currentTab) {
            Tab.PLAYERS -> selectedPlayer?.lastKnownName ?: "Select a player or tag new"
            Tab.TYPES -> selectedType?.displayName ?: "Create new type"
        }
        context.drawText(textRenderer, rightTitle, rightPanelX + 8, contentY + 6, NlibTheme.TEXT_PRIMARY, true)

        // Draw attribute type labels next to dropdowns
        if (currentTab == Tab.PLAYERS && selectedPlayer != null) {
            val types = PlayerTagModel.getAttributeTypes()
            var y = contentY + 30
            for (type in types) {
                context.drawText(textRenderer, type.displayName, rightPanelX + 10, y + 6, NlibTheme.TEXT_PRIMARY, false)
                y += 28
            }
        }

        // Render dropdown overlays on top of everything
        for (dropdown in dropdowns) {
            dropdown.render(context, mouseX, mouseY, delta)
        }
    }

    override fun mouseClicked(mouseX: Double, mouseY: Double, button: Int): Boolean {
        // Handle dropdown clicks first (they render on top)
        for (dropdown in dropdowns) {
            if (dropdown.mouseClicked(mouseX, mouseY, button)) return true
        }
        return super.mouseClicked(mouseX, mouseY, button)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontalAmount: Double, verticalAmount: Double): Boolean {
        // Handle dropdown scrolling first
        for (dropdown in dropdowns) {
            if (dropdown.mouseScrolled(mouseX, mouseY, verticalAmount)) return true
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)
    }

    override fun close() {
        PlayerTagModel.save()
        super.close()
    }

    private fun parseColor(input: String): Int? {
        val namedColors = mapOf(
            "red" to 0xFF5555,
            "green" to 0x55FF55,
            "blue" to 0x5555FF,
            "yellow" to 0xFFFF55,
            "cyan" to 0x55FFFF,
            "magenta" to 0xFF55FF,
            "white" to 0xFFFFFF,
            "gray" to 0xAAAAAA,
            "orange" to 0xFFAA00
        )

        namedColors[input.lowercase()]?.let { return it }

        val hex = input.removePrefix("#").removePrefix("0x")
        return try {
            hex.toInt(16)
        } catch (e: Exception) {
            null
        }
    }

    // === List Widgets ===

    private inner class PlayerListWidget(
        client: MinecraftClient,
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
        override fun render(
            context: DrawContext, index: Int, y: Int, x: Int,
            entryWidth: Int, entryHeight: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float
        ) {
            val selected = (playerList?.selectedOrNull === this)
            renderBackground(context, x, y, entryWidth, entryHeight, hovered, selected)

            val font = client!!.textRenderer
            context.drawText(font, player.lastKnownName, x + 8, y + 6, Colors.TEXT, true)

            // Show attributes
            val attrs = player.attributes.entries.mapNotNull { (typeId, valueId) ->
                val type = PlayerTagModel.getAttributeType(typeId) ?: return@mapNotNull null
                val value = type.getValue(valueId) ?: return@mapNotNull null
                value.displayName
            }.joinToString(", ")

            if (attrs.isNotEmpty()) {
                context.drawText(font, "§7$attrs", x + 8, y + 18, Colors.TEXT_SECONDARY, false)
            }

            // Show notes preview
            if (player.notes.isNotEmpty()) {
                val notesPreview = if (player.notes.length > 20) player.notes.take(20) + "..." else player.notes
                context.drawText(font, "§8$notesPreview", x + 8, y + 28, Colors.TEXT_SECONDARY, false)
            }
        }
    }

    private inner class TypeListWidget(
        client: MinecraftClient,
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
        override fun render(
            context: DrawContext, index: Int, y: Int, x: Int,
            entryWidth: Int, entryHeight: Int,
            mouseX: Int, mouseY: Int, hovered: Boolean, tickDelta: Float
        ) {
            val selected = (typeList?.selectedOrNull === this)
            renderBackground(context, x, y, entryWidth, entryHeight, hovered, selected)

            val font = client!!.textRenderer
            context.drawText(font, type.displayName, x + 8, y + 6, Colors.TEXT, true)

            val valueCount = type.values.size
            context.drawText(font, "§7$valueCount values, priority ${type.renderPriority}", x + 8, y + 18, Colors.TEXT_SECONDARY, false)
        }
    }
}
