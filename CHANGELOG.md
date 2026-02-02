# Changelog

## 0.3.1

### Added
- **Overlay Visibility Toggle** - New keybind to temporarily hide all overlays without disabling them
- **Auto-focus Handbook Search** - Search box now auto-focuses when opening the handbook
- **Mining XP Documentation** - Added mining XP and class requirements to all ore item documentation
- **New Ore Items** - Added ancient debris, calcite, deepslate, nether ores, and obsidian to handbook

### Changed
- Handbook search box now spans the full header width
- Refactored handbook pages to use `.mdx` format with frontmatter for auto-discovery
- Fixed armor progression: Leather → Copper → Iron → Diamond → Netherite
- Marked Gold and Chainmail armor as dead ends (cannot upgrade)
- Updated copper armor recipes and added copper armor items
- Fixed emerald ore XP value (50 → 15)

### Fixed
- Handbook no longer opens while typing in text fields (creative search, etc.)
- Fixed EditBox focus detection for handbook keybind

### Technical
- Added multi-version build system supporting Minecraft 1.21.6, 1.21.9, and 1.21.11
- Ported to Minecraft 1.21.11 API changes
- Updated to NLib 0.1.1
- Bundled snakeyaml in JAR for runtime

## 0.3.0

### Added
- **Handbook** - In-game guide with searchable documentation for CivMC items, recipes, and mechanics
  - Full-text search with match type indicators
  - Item database with recipe rendering (shaped, shapeless, smelting)
  - Inline item syntax and item links in markdown
  - Inventory integration - hover over items to open handbook pages
  - Class unlock components showing items unlocked at each level
  - Draggable scrollbar with smooth scrolling
  - Code block copy functionality

## 0.2.0

### Added
- **Player Tags** - Tag and track players with custom attributes, icons above heads, and styled name tags in tab list
- **Repair Calculator Overlay** - Shows repair information for held items and targeted blocks
- **Bed Healing Overlay** - Displays healing progress when resting in a bed
- **Combat Timer Overlay** - Shows combat log timer countdown for safe logout
- **Server Feature Detection** - Detects server capabilities via plugin channels

### Changed
- Enhanced Class XP overlay with detailed XP tracking
- Refactored config system to use NLib
- Improved core overlay system and GUI

### Removed
- Health Warning feature

## 0.1.0

The initial release. Adds the overlay editor, config menu, and the following features and overlays:

- Health Warning
- Auto Sit
- Block Count
- Class XP
