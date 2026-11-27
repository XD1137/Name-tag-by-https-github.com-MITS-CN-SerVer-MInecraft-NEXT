# NekoGate NameTag

A standalone Java extension for [NekoGate](https://github.com/MITS-CN/SerVer-MInecraft-NEXT).
It manages persistent player presentation names through the **NekoGate Extension API v1.4**.

The extension changes presentation data only. It never changes authenticated player names,
UUIDs, chat message bodies, or message signatures.

## Features

- Name-keyed configuration: player names are the keys; UUIDs are not written as configuration keys.
- Prefix, display name, suffix, HEX color, and two-color gradient commands.
- Supports the core's component JSON format, including nested `extra` components.
- Atomic configuration writes and automatic API reload after edits.
- Permission-protected `/nametag` command.
- Existing UUID-keyed entries are left intact for the core's legacy-configuration compatibility.

## Install

1. Build with Java 17 and Gradle 8.10.2 or newer:

   ```bash
   gradle clean build
   ```

2. Copy `build/libs/nekogate-nametag-1.0.0.jar` into:

   ```
   plugins/NekoGate/extensions/
   ```

3. Start or restart ViaProxy with NekoGate installed. NekoGate loads extensions at startup using Java `ServiceLoader`.

The JAR bundles Gson but does not bundle NekoGate API classes. The API stubs in `api-stubs/` are compile-time
snapshots of API v1.4 and are excluded from the extension JAR.

## Commands

All commands require `nekogate.nametag`. NekoGate denies extension permissions by default. Grant the permission
to an administrator in `plugins/NekoGate/permissions.properties`, using the administrator's UUID:

```properties
00000000-0000-0000-0000-000000000000=nekogate.nametag
```

The permission file is read at NekoGate startup; restart ViaProxy after changing it.

| Command | Description |
|---|---|
| `/nametag` | Show help |
| `/nametag set <player-name> <name>` | Set a display name |
| `/nametag prefix <player-name> <text>` | Set a prefix |
| `/nametag suffix <player-name> <text>` | Set a suffix |
| `/nametag color <player-name> <#RRGGBB>` | Set the name color |
| `/nametag gradient <player-name> <start> <end> <text>` | Set a two-color gradient |
| `/nametag clear <player-name>` | Remove the configured override |
| `/nametag reload` | Reload the configuration from disk |

Quote arguments containing spaces:

```text
/nametag set Steve "The Builder"
/nametag prefix Steve "[VIP] "
/nametag gradient Steve #FFAA00 #FF00AA "Hello Steve"
/nametag color Steve #55FFFF
```

Names may be edited while the player is offline; the supplied Minecraft name is used as the key.
Player names are case-insensitive for lookup in NekoGate, while the spelling entered by the command is
preserved in the JSON file.

## Configuration

The extension edits the same file read by the NekoGate core:

`./data/name.josn5`

The filename is intentionally `name.josn5`. Despite the extension, the core currently expects **strict JSON**,
not the full JSON5 syntax.

Example:

```json
{
  "players": {
    "Steve": {
      "prefix": {
        "text": "[VIP] ",
        "color": "#55FFFF"
      },
      "name": {
        "text": "The Builder",
        "gradient": ["#FFAA00", "#FF00AA"],
        "bold": true
      },
      "suffix": " ★"
    }
  }
}
```

Each of `prefix`, `name`, and `suffix` accepts a plain string or a component object. Component objects support
`text`, `color` (`#RRGGBB`), `gradient` (a two-color array), `bold`, `italic`, and recursive `extra` arrays.

The extension uses `api.dataDirectory()` to locate the shared file. Updates are written to a temporary file and
atomically moved into place where supported. Other player entries are preserved.

## Compatibility and scope

- NekoGate Extension API **1.4** (major version 1).
- Java 17+.
- The core currently applies configured names to chat and the Tab list for Minecraft 26.3.
- **Overhead entity nametag rewriting is not exposed by the current extension API.** It requires a core-side
  packet handler that maps player entity IDs to player names and rewrites entity metadata. This extension
  prepares and manages the same name-keyed data, but does not claim to rewrite packets on its own.
- Clients that connect directly to the backend without passing through ViaProxy cannot receive proxy-side rewrites.

## Build artifact

`build/libs/nekogate-nametag-1.0.0.jar`

GitHub Actions builds the extension on pushes and pull requests.
