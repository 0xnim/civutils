# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CivUtils is a Minecraft Fabric client-side mod (1.21.8) written in Kotlin, providing quality-of-life utilities for Civ multiplayer servers. It uses Fabric Loom for building and depends on the NLib UI library.

## Build Commands

```bash
./gradlew build              # Build the mod (outputs to build/libs/)
./gradlew runClient          # Run Minecraft with the mod loaded
./gradlew classes            # Compile without full build
```

## Architecture

The mod follows a manager-based architecture inspired by Wynntils:

### Core Systems (in `core/`)

- **CivutilsMod** (`CivutilsMod.kt`) - Central singleton providing access to all managers
- **EventBus** - Publish/subscribe event system using `@Subscribe` annotation
- **ConfigManager** - Handles persistence of `@Persisted` annotated `Config<T>` fields to JSON
- **FeatureManager** - Manages feature lifecycle (enable/disable, event registration)
- **OverlayManager** - Manages HUD overlays
- **ModelManager** - Manages data models that track game state

### Key Abstractions

**Feature** (`core/feature/Feature.kt`):
- Extend `Feature` class, annotate with `@ConfigCategory(Category.X)`
- Use `@Persisted` on `Config<T>` fields for auto-persistence
- Use `@Subscribe` on methods to receive events
- Override `onEnable()`/`onDisable()` for lifecycle hooks

**Overlay** (`core/overlay/Overlay.kt`):
- Extend `Overlay` with position and size
- Override `render(DrawContext, Float)` for HUD rendering
- Uses anchor-based positioning (screen divided into 3x3 grid)

**Model** (`core/model/Model.kt`):
- Singleton objects extending `Model` for tracking game state
- Always active, auto-reset on world join/leave
- Separates data collection from features/overlays

**Config** (`core/config/Config.kt`):
- Wrap values in `Config<T>` with optional validator
- Use `intConfig(default, min, max)` or `floatConfig()` helpers for range validation

### Registration Flow

In `CivutilsClient.onInitializeClient()`:
1. `CivutilsMod.initialize()` - Creates managers
2. Register models, features, overlays
3. `configManager.loadAll()` - Load persisted configs
4. `featureManager.initializeAll()` - Enable features based on userEnabled

### Source Structure

- `src/main/` - Common code (minimal, just mod initializer)
- `src/client/kotlin/` - Client-side Kotlin code
- `src/client/java/.../mixin/client/` - Mixin classes for hooking into Minecraft
- `src/client/resources/` - Client assets and mixin config

### Mixins

Client mixins are in `xyz.nim.civutils.mixin.client` and configured in `civutils.client.mixins.json`. Mixins fire events through the EventBus for features to consume.

## Key Conventions

- Features use categories: COMBAT, CHAT, INVENTORY, MAP, OVERLAYS, PLAYERS, UTILITIES, DEBUG
- Config keybind: Right Shift opens config menu
- Configs stored in `.minecraft/config/civutils/config.json`
