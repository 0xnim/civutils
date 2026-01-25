# CivUtils

A lightweight client-side mod for Civ multiplayer servers. Adds practical HUD overlays and quality-of-life features without cluttering your screen.

## Features

### Utilities
- **AutoSit** — Automatically sends `/sit` after a configurable period of inactivity to prevent hunger drain.

### Players
- **Player Tags** — Tag and track players with custom attributes. Adds colored name tags, tab list styling, and icons above player heads. Tracks last-seen locations.

### Overlays
- **Block Count** — Displays the total count of your held block type across your entire inventory. Essential for large builds.
- **Class XP** — Real-time XP progress bars for your class and specializations. Shows XP rate per hour, estimated time to next level, and session XP gained.
- **Combat Timer** — Shows the combat log countdown so you know exactly when it's safe to log out.
- **Bed Healing** — Displays healing progress while resting in a bed, with percentage and time remaining.
- **Repair Calculator** — Shows repair costs when holding damaged tools: number of repairs needed, hunger cost, XP levels, and Blacksmith XP gained.

## Configuration

Press **Right Shift** to open the config menu. Every feature can be toggled individually and customized—overlay positions, colors, bar sizes, and more.

## Requirements

- Minecraft 1.21.4
- Fabric Loader
- Fabric API
- [NLib](https://modrinth.com/mod/nlib)
