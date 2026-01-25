---
title: Beacons
summary: Custom beacon mechanics with ownership and effects
---

# Beacons

Beacons in CivLabs have a custom recipe and function completely differently from vanilla. They provide effects to whitelisted players, negative effects to others, and require maintenance.

## Crafting

**Recipe:** 5 Glass + 3 Obsidian + 1 Nether Star

**Requirements to craft materials:**
- Level 3 [Healer](healer)
- Level 3 [Blacksmith](blacksmith)
- Level 3 [Miner](miner)
- Level 1 [Librarian](librarian)

Alternatively, Level 5 Librarians can craft beacons directly.

## Setup

1. **Place the beacon** - You become the "owner"
2. **Nearby players are alerted** to its placement
3. **10-minute warmup** - No effects during this time
4. **Warmup complete** - Another alert, effects begin

## Ownership & Whitelist

The beacon owner can whitelist players using a **Book & Quill**:
- Right-click the beacon with the book to add to whitelist
- Whitelisted players receive **positive effects**
- Non-whitelisted players receive **negative effects**
- All whitelisted players also get passive **Absorption**

## Effect Selection

Choose one effect type when setting up:

| Selection | Whitelisted Effect | Non-Whitelisted Effect |
|-----------|-------------------|------------------------|
| **Speed** | Speed II | Slowness II |
| **Haste** | Haste I | Mining Fatigue I |
| **Resistance** | None | Resistance I |
| **Strength** | Health Boost II | None |
| **Regeneration** | None | Glowing |

## Beacon Beam

The beacon beam has special properties:
- **Automatically clears** blocks above it
- **Burns players** who touch it

## Maintenance

Beacons require constant fuel to stay active:

**Fuel consumption (every 25 seconds):**
- 8 Coal/Charcoal, OR
- 1 Metal Ingot (copper, iron, or gold)

**Fuel source:**
- Place a chest within 1 block of the beacon
- Beacon automatically pulls fuel from the chest

**Fuel costs for a 3-hour session:**
- ~6.75 stacks of ingots, OR
- ~54 stacks (full double chest) of coal/charcoal

## Strategic Uses

1. **Base Defense:** Slow/fatigue enemies, speed up defenders
2. **Territory Marking:** Glowing effect reveals intruders
3. **Mining Operations:** Haste for friendly miners
4. **Combat Support:** Health boost for your fighters

## Tips

1. **Plan Fuel:** Stock up before activating
2. **Hidden Chests:** Keep fuel supply protected
3. **Multiple Beacons:** Different areas can have different effects
4. **Whitelist Management:** Keep it updated with current allies

---

> **See Also:** [Librarian](librarian), [Builder](builder)
