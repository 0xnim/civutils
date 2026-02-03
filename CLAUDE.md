# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CivUtils is a Minecraft Fabric client-side mod written in Kotlin, providing quality-of-life utilities for Civ multiplayer servers. It uses Fabric Loom for building and depends on the NLib UI library (from mavenLocal).

## Build Commands

```bash
./gradlew build              # Build the mod (outputs to build/libs/)
./gradlew runClient          # Run Minecraft with the mod loaded
./gradlew classes            # Compile without full build
./gradlew generateItemManifest   # Regenerate handbook items manifest
./gradlew generatePageManifest   # Regenerate handbook pages manifest
```

**Multi-version builds:** The mod supports multiple Minecraft versions. Version-specific properties are in `versions/` directory. Build for a specific version with:
```bash
./gradlew build -Pmc=1.21.6   # Build for MC 1.21.6
./gradlew build -Pmc=1.21.9   # Build for MC 1.21.9
./gradlew build -Pmc=1.21.11  # Build for MC 1.21.11 (default)
```

**Note:** NLib must be installed to mavenLocal first (`./gradlew publishToMavenLocal` in the nlib repo).

## Architecture

The mod follows a manager-based architecture inspired by Wynntils:

### Core Systems (in `core/`)

- **CivutilsMod** (`core/CivutilsMod.kt`) - Central singleton providing access to all managers
- **EventBus** (`core/event/EventBus.kt`) - Publish/subscribe event system using `@Subscribe` annotation
- **CivutilsConfigManager** (`core/config/CivutilsConfigManager.kt`) - Wraps NLib's config system for dynamic registration
- **FeatureManager** - Manages feature lifecycle (enable/disable, event registration)
- **OverlayManager** - Manages HUD overlays
- **ModelManager** - Manages data models that track game state

### Key Abstractions

**Feature** (`core/feature/Feature.kt`):
- Extend `Feature` class, annotate with `@ConfigCategory(Category.X)`
- Use NLib config options (via `NlibConfigExtensions.kt` helpers)
- Override `getConfigs()` to return all config options for persistence
- Use `@Subscribe` on methods to receive events
- Override `onEnable()`/`onDisable()` for lifecycle hooks

**Overlay** (`core/overlay/Overlay.kt`):
- Extend `Overlay` with `OverlayPosition` and `OverlaySize`
- Override `render(GuiGraphics, Float)` for HUD rendering
- Uses anchor-based positioning (screen divided into 3x3 grid)
- Override `getConfigs()` to include overlay-specific settings
- For text overlays, extend `TextOverlay` and override `getTemplate()` / `getPreviewTemplate()` instead

**Model** (`core/model/Model.kt`):
- Singleton objects extending `Model` for tracking game state
- Always active, auto-reset on world join/leave
- Separates data collection from features/overlays

**Config** (`core/config/NlibConfigExtensions.kt`):
- Uses NLib's `ConfigOption<T>` system
- Helper functions: `booleanConfig()`, `intConfig()`, `floatConfig()`, `stringConfig()`, `colorConfig()`, `enumConfig()`
- Access values with `.value` property extension
- Configs auto-persist via `CivutilsConfigManager`
- Use `.onChange { }` extension for config change callbacks

**Events** (`core/event/Events.kt`):
- All events defined in `Events.kt`, extend `Event()` or `CancellableEvent()`
- Subscribe with `@Subscribe` annotation on methods
- Common events: `ClientTickEvent`, `HudRenderEvent`, `WorldJoinEvent`, `WorldLeaveEvent`, `ChatMessageReceivedEvent`, `ActionBarMessageEvent`

### Registration Flow

In `CivutilsClient.onInitializeClient()`:
1. `CivutilsMod.initialize()` - Creates managers
2. `KeybindManager.register()` - Register keybinds
3. Register commands, models, features, overlays (order matters: models before features)
4. `configManager.loadAll()` - Load persisted configs
5. `featureManager.initializeAll()` - Enable features based on userEnabled

### Source Structure

- `src/main/` - Common code (minimal, just mod initializer)
- `src/client/kotlin/xyz/nim/civutils/` - Client-side Kotlin code
  - `client/` - Entry point (`CivutilsClient.kt`)
  - `core/` - Core framework (managers, base classes)
  - `features/` - Feature implementations by category
  - `overlays/` - Overlay implementations
  - `models/` - Data models
  - `gui/` - Screens and widgets
  - `data/` - Data classes (e.g., player tags)
  - `utils/` - Utility classes
- `src/client/java/.../mixin/client/` - Mixin classes for hooking into Minecraft
- `src/client/resources/` - Client assets and mixin config

### Mixins

Client mixins are in `xyz.nim.civutils.mixin.client` (Java) and configured in `civutils.client.mixins.json`. Mixins fire events through `CivutilsMod.INSTANCE.getEventBus().post(event)` for features to consume. Cancellable events use `ci.cancel()` when `event.getCancelled()` is true.

### Network/Plugin Channels

**CivChannelManager** (`core/network/CivChannelManager.kt`):
- Handles server-to-client plugin channel communication
- Registers payload types in `CivPayloads.kt`
- Used for server feature detection (e.g., CivMC-specific features)

## Handbook System

The handbook is an in-game knowledge base with 27 pages and 245 item definitions. Content is authored in MDX format (YAML frontmatter + Markdown).

### Asset Structure

```
src/client/resources/assets/civutils/handbook/
├── index.json              # Categories and default page
├── pages-manifest.json     # Generated list of page files
├── items-manifest.json     # Generated list of item files
├── pages/                  # Handbook content pages
│   ├── classes/            # Class-related pages (14 pages)
│   ├── mechanics/          # Game mechanics pages (8 pages)
│   └── *.mdx               # Top-level pages
└── items/                  # Item definitions by category
    ├── armor/              # 54 items
    ├── brewing/            # 5 items
    ├── food/               # 6 items
    ├── materials/          # 80 items
    ├── misc/               # 24 items
    ├── tools/              # 49 items
    └── weapons/            # 10 items
```

### MDX File Format

**Page MDX** (`pages/*.mdx`):
```yaml
---
title: Page Title
summary: Brief description for search
category: getting-started    # Must match index.json category ID
order: 0                     # Display order within category
tags: ["tag1", "tag2"]       # Optional search tags
related: ["other-page-id"]   # Optional related pages
itemId: "minecraft:item"     # Optional - shows item icon in header
---

Markdown content here...
```

**Item MDX** (`items/{category}/*.mdx`):
```yaml
---
id: item-id
name: Display Name
summary: Brief description
category: MATERIALS          # MATERIALS, TOOLS, ARMOR, WEAPONS, FOOD, BREWING, MISC
tags: [searchable, tags]
order: 0
displayItem: "minecraft:iron_ingot"  # Vanilla item to render
requiredClass: "blacksmith:3"        # Optional class unlock
recipes:                             # How to CRAFT this item (not what it's used in)
  - type: CRAFTING_SHAPED
    pattern: ["AAA", "ABA", "AAA"]
    key:
      A: { item: "minecraft:iron_ingot" }
      B: { item: "minecraft:stick" }
    outputs:
      - { item: "minecraft:iron_block" }
---

Item description in markdown...
```

**Recipe Types:** `CRAFTING_SHAPED`, `CRAFTING_SHAPELESS`, `CRAFTING_2X2`, `SMELTING`, `BLASTING`, `SMOKING`, `CAMPFIRE`, `SMITHING`, `BREWING`, `STONECUTTING`, `CARTOGRAPHY`, `CUSTOM`

### Manifest Generation

Manifests are auto-generated before build. Regenerate manually with:
```bash
./gradlew generateItemManifest   # Regenerate items-manifest.json
./gradlew generatePageManifest   # Regenerate pages-manifest.json
```

### Key Components

- **HandbookModel** (`models/HandbookModel.kt`) - Content loading, search, navigation
- **HandbookFeature** (`features/utilities/HandbookFeature.kt`) - Keybind (`H`) and inventory item lookup
- **HandbookScreen** (`gui/screens/HandbookScreen.kt`) - Two-panel GUI with categories, search, markdown rendering
- **HandbookData.kt** - Data classes: `HandbookIndex`, `HandbookCategory`, `HandbookPageMeta`, `HandbookPage`
- **ItemDatabase.kt** - Item data classes: `ItemDefinition`, `Recipe`, `RecipeSlot`, `ItemCategory`
- **MdxParser** (`core/handbook/MdxParser.kt`) - YAML frontmatter parsing
- **MarkdownParser** (`core/handbook/MarkdownParser.kt`) - Markdown to `MarkdownElement` conversion

### Markdown Features

Standard markdown plus:
- **Inline items:** `[[item_id]]` or `[[item_id|count]]` - renders item icon
- **Item links:** `[text](item:item_id)` - clickable link to item page
- **Recipe blocks:** ` ```recipe ... ``` ` - renders recipe visualization
- **Class unlocks:** `<Unlocks class="blacksmith" level="1" />` - shows unlock requirement

### Server Overrides

Server-specific content loads from `~/.minecraft/config/civutils/handbook/<server-hash>/` and merges with bundled content.

## Key Conventions

- Features use categories: COMBAT, CHAT, INVENTORY, MAP, OVERLAYS, PLAYERS, UTILITIES, DEBUG
- Keybinds: Right Shift opens config menu, overlay editor is unbound by default (configurable in Minecraft controls)
- Configs stored in `.minecraft/config/civutils/`
