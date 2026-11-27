# NekoGate NameTag

A standalone Java extension for [NekoGate](https://github.com/MITS-CN/SerVer-MInecraft-NEXT). It manages player **presentation names** through the NekoGate Extension API v1.2.

It changes only the display component used by chat and the player list. It does **not** change authenticated usernames, UUIDs, chat message bodies, or message signatures.

## Install

1. Build with Java 17 and Gradle 8.10.2 or newer:

   ```bash
   gradle clean build
   ```

2. Copy `build/libs/nekogate-nametag-1.0.0.jar` into:

   ```
   plugins/NekoGate/extensions/
   ```

3. Start/restart ViaProxy with the NekoGate plugin installed. NekoGate loads extensions at startup using Java `ServiceLoader`.

The JAR includes Gson, but intentionally does **not** bundle NekoGate API classes. The API stubs in `api-stubs/` are compile-time snapshots of the upstream v1.2 API only.

## Commands

All commands require the permission `nekogate.nametag`. NekoGate denies extension permissions by default. Grant it to an administrator in `plugins/NekoGate/permissions.properties`, using the player's UUID:

```properties
00000000-0000-0000-0000-000000000000=nekogate.nametag
```

The permissions file is read at NekoGate startup; restart ViaProxy after changing it.

| Command | Description |
|---|---|
| `/nametag` | Show help |
| `/nametag set <player|uuid> <name>` | Set a persistent display name |
| `/nametag prefix <player|uuid> <text>` | Set a prefix |
| `/nametag suffix <player|uuid> <text>` | Set a suffix |
| `/nametag color <player|uuid> <#RRGGBB>` | Set the name color |
| `/nametag gradient <player|uuid> <start> <end> <text>` | Set a two-color gradient |
| `/nametag clear <player|uuid>` | Remove the configured override |
| `/nametag reload` | Reload the configuration from disk |

Quote arguments containing spaces, for example:

```text
/nametag set Steve "The Builder"
/nametag prefix Steve "[VIP] "
/nametag gradient Steve #FFAA00 #FF00AA "Hello Steve"
/nametag color Steve #55FFFF
```

A player name must be online so NekoGate can resolve its UUID. Offline players can be addressed by UUID.

## Configuration

The extension edits the same file read by the NekoGate core:

`./data/name.josn5`

The filename is intentionally `name.josn5`. Despite the extension, the current core parser expects **strict JSON**, not the full JSON5 syntax.

Example:

```json
{
  "players": {
    "00000000-0000-0000-0000-000000000000": {
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

Commands write changes atomically, preserve other players' entries, and call the API's `displayNames().reload()` so the core packet handlers pick them up. The core API's `set()` method is runtime-only; this extension persists command changes in the shared configuration file.

## Compatibility

- NekoGate Extension API **1.2 or newer** (major version 1).
- Java 17+.
- Chat and Tab packet presentation is performed by the NekoGate core; this extension supplies and manages the configured values.
- The current upstream packet rewrite implementation targets Minecraft 26.3.

## Build artifact

`build/libs/nekogate-nametag-1.0.0.jar`

GitHub Actions builds the extension on pushes and pull requests.
