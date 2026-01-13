# CivUtils Protocol Specification

**Version:** 1.0.0
**Protocol Version:** 1

This document specifies the plugin channel protocol for communication between Civ-style Minecraft servers and the CivUtils client mod.

---

## Overview

CivUtils uses Minecraft plugin channels for structured client-server communication. All channels use the `civ:` namespace and JSON payloads encoded as UTF-8 strings.

### Channel Registration

Servers should register channels using the standard Minecraft plugin channel mechanism. The client will attempt to communicate on channels it supports; servers that don't implement a channel can simply ignore messages.

---

## Channel: `civ:handshake`

Capability negotiation between client and server.

### Client → Server

Sent when the player joins the server.

```json
{
  "client": "civutils",
  "version": "1.0.0",
  "protocol": 1,
  "channels": ["civ:class_xp"]
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `client` | string | Yes | Client identifier, always `"civutils"` |
| `version` | string | Yes | Client mod version (semver) |
| `protocol` | integer | Yes | Protocol version number |
| `channels` | string[] | Yes | List of channels the client supports |

### Server → Client

Response with server capabilities and feature configuration.

```json
{
  "server": "MyCivServer",
  "version": "2.3.1",
  "protocol": 1,
  "channels": ["civ:class_xp"],
  "features": {
    "classes": {
      "enabled": true,
      "config": {
        "maxLevel": 5,
        "classes": ["Farmer", "Builder", "Miner", "Healer", "Librarian", "Guardsman", "Blacksmith"],
        "xpFormula": "2*(25*lvl^2+5*lvl+200*2.45^lvl)-400"
      }
    },
    "combatTimer": {
      "enabled": true,
      "config": {
        "timerDuration": 30,
        "logoutPenalty": "death"
      }
    },
    "bedHealing": {
      "enabled": true,
      "config": {
        "requiresCampfire": true,
        "healRate": 1.0
      }
    }
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `server` | string | Yes | Server name/identifier |
| `version` | string | Yes | Server plugin version |
| `protocol` | integer | Yes | Protocol version (must match client) |
| `channels` | string[] | Yes | Channels the server will use |
| `features` | object | Yes | Feature availability and configuration |

### Feature Object Structure

Each feature in the `features` object has the following structure:

```json
{
  "enabled": true,
  "config": { ... }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `enabled` | boolean | Yes | Whether this feature is active on the server |
| `config` | object | No | Feature-specific configuration values |

### Standard Features

#### `classes`

Class/skill progression system.

| Config Field | Type | Description |
|--------------|------|-------------|
| `maxLevel` | integer | Maximum skill level (typically 5) |
| `classes` | string[] | Available class names |
| `xpFormula` | string | XP calculation formula (informational) |

#### `combatTimer`

Combat logging protection system.

| Config Field | Type | Description |
|--------------|------|-------------|
| `timerDuration` | integer | Combat timer duration in seconds |
| `logoutPenalty` | string | Penalty for logging out in combatTimer (`"death"`, `"none"`, etc.) |

#### `bedHealing`

Bed-based healing mechanic.

| Config Field | Type | Description |
|--------------|------|-------------|
| `requiresCampfire` | boolean | Whether a lit campfire is required nearby |
| `healRate` | number | Healing rate multiplier |

---

## Channel: `civ:class_xp`

Real-time class/skill XP updates.

### Message Types

All messages have a `type` field indicating the message type.

#### Type: `full`

Complete class state. Sent on join or when full refresh is needed.

```json
{
  "type": "full",
  "currentClass": "Guardsman",
  "classes": {
    "Guardsman": {
      "level": 2,
      "levelName": "Apprentice",
      "totalXp": 3500.0,
      "currentXp": 1279,
      "xpForLevel": 3741
    },
    "Miner": {
      "level": 1,
      "levelName": "Novice",
      "totalXp": 800.0,
      "currentXp": 160,
      "xpForLevel": 1581
    }
  }
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Always `"full"` |
| `currentClass` | string | No | Currently active class (if any) |
| `classes` | object | Yes | Map of class name to class data |

**Class Data Object:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `level` | integer | Yes | Current level (0 to maxLevel) |
| `levelName` | string | No | Display name for level (e.g., "Novice") |
| `totalXp` | number | Yes | Total accumulated XP |
| `currentXp` | integer | Yes | XP earned toward next level |
| `xpForLevel` | integer | Yes | XP required to reach next level |

#### Type: `partial`

Single class update. Sent frequently during gameplay.

```json
{
  "type": "partial",
  "class": "Guardsman",
  "totalXp": 3513.0,
  "change": 13.0,
  "currentClass": "Guardsman"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Always `"partial"` |
| `class` | string | Yes | Class name being updated |
| `totalXp` | number | Yes | New total XP value |
| `change` | number | Yes | XP change amount (positive or negative) |
| `currentClass` | string | No | Currently active class (may change) |

#### Type: `levelup`

Level increase notification.

```json
{
  "type": "levelup",
  "class": "Guardsman",
  "level": 3,
  "levelName": "Journeyman",
  "totalXp": 5962.0
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Always `"levelup"` |
| `class` | string | Yes | Class that leveled up |
| `level` | integer | Yes | New level |
| `levelName` | string | No | Display name for new level |
| `totalXp` | number | Yes | Total XP at level up |

#### Type: `leveldown`

Level decrease notification.

```json
{
  "type": "leveldown",
  "class": "Guardsman",
  "level": 2,
  "levelName": "Apprentice",
  "totalXp": 2200.0
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `type` | string | Yes | Always `"leveldown"` |
| `class` | string | Yes | Class that leveled down |
| `level` | integer | Yes | New level |
| `levelName` | string | No | Display name for new level |
| `totalXp` | number | Yes | Total XP after level down |

---

## Implementation Notes

### For Server Developers

1. **Channel Registration:** Register `civ:handshake` and any data channels you support
2. **Handshake Flow:** Wait for client handshake, then respond with server capabilities
3. **Feature Flags:** Only include features your server actually implements
4. **Partial Updates:** Use `partial` messages for frequent XP changes to reduce bandwidth
5. **Full Sync:** Send `full` message on player join and periodically for consistency

### For Client Developers

1. **Fallback:** When server doesn't respond to handshake, fall back to parsing game messages
2. **Unknown Features:** Ignore features not recognized by the client version
3. **Protocol Mismatch:** If server protocol version differs, fall back to message parsing
4. **Config Caching:** Cache server feature config for the session duration

---

## Version History

| Version | Protocol | Changes |
|---------|----------|---------|
| 1.0.0 | 1 | Initial specification |
