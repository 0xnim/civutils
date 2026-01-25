# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CivUtils is a Minecraft Fabric client-side mod (1.21.8) written in Kotlin, providing quality-of-life utilities for Civ multiplayer servers. It uses Fabric Loom for building and depends on the NLib UI library (from mavenLocal).

## Build Commands

```bash
./gradlew build              # Build the mod (outputs to build/libs/)
./gradlew runClient          # Run Minecraft with the mod loaded
./gradlew classes            # Compile without full build
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

## Key Conventions

- Features use categories: COMBAT, CHAT, INVENTORY, MAP, OVERLAYS, PLAYERS, UTILITIES, DEBUG
- Keybinds: Right Shift opens config menu, overlay editor is unbound by default (configurable in Minecraft controls)
- Configs stored in `.minecraft/config/civutils/`
