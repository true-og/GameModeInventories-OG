## GameModeInventories-OG

Allow players to have separate inventories for each game mode (Creative, Survival and Adventure).

This is a [TrueOG Network](https://trueog.net) maintained soft fork of [eccentricdevotion/GameModeInventories](https://github.com/eccentricdevotion/GameModeInventories).

### Changes from Upstream

- Build system migrated from Maven to Gradle (Kotlin DSL), Gradle 8.14.3, Shadow 8.3.9, Spotless with TrueOG Network's eclipse-based Java Formatting.
- `config.yml` is read-only at runtime — the plugin no longer mutates or rewrites it. A default `config.yml` is copied into the plugin folder on first run if missing.
- Compatible with [NoClip-OG](https://github.com/true-og/NoClip-OG) (1.3.0+). When `restrict_spectator` is enabled, players holding the `noclip.use` permission are exempt from the restriction so NoClip's creative/spectator toggle works without being cancelled or spamming chat.
- Creative is staff only: it needs `gamemodeinventories.toggle` *and* a region in `creative_regions`, or `gamemodeinventories.anywhere` to ignore both. Spectator needs `gamemodeinventories.spectator` or `noclip.use`. Adventure is refused in `restrict_adventure_worlds`.
- Gamemode rules live in one place, `api/GameModePolicy`, which every enforcement point in the plugin asks. It is published for other plugins so they enforce the same rule instead of reimplementing it: fetch it from the Bukkit services manager, or reflectively via `GameModeInventories#getGameModePolicy()`. `mayUse(player, gameMode, location)` answers for any gamemode; survival is always allowed, adventure never is.
- Creative is enforced on movement and teleports, judged at the destination, and unsanctioned creative is forced back to survival with one-time fall protection. Gamemode switches pre-validated through the policy (`internalGameModeChange`) are trusted rather than policed a second time.
- Login is deferred to [Spawn-OG](https://github.com/true-og/Spawn-OG) when it is installed. It normalizes gamemode inside a safety transaction that also relocates the player, so forcing survival underneath it would race that transaction. Spawn-OG also uses the policy and `internalGameModeChange` to hand creative or spectator back after a rescue or `/spawnback` return. Without Spawn-OG, GameModeInventories-OG still forces survival on join for creative players outside a creative region.
- Dropped LogBlock integration. Block-removal logging now runs exclusively through CoreProtect / CoreProtect-OG.
- Targets Purpur API 1.19.4 (Java 17, GraalVM toolchain).

### Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/gmi [option]` (alias `/gmic`) | Toggle creative mode, or change config options | `gamemodeinventories.toggle` / `gamemodeinventories.anywhere` to toggle; `gamemodeinventories.admin` for config |

### Permissions

- `gamemodeinventories.use` - Separate per-gamemode inventories (default: true)
- `gamemodeinventories.toggle` - Toggle creative via `/gmic` inside a `creative_regions` region (default: op)
- `gamemodeinventories.anywhere` - Hold and toggle creative anywhere, exempt from forced survival (default: op)
- `gamemodeinventories.spectator` - Hold spectator mode (default: op)
- `gamemodeinventories.death` - Keep separate death handling (default: op)
- `gamemodeinventories.bypass` - Bypass restrictions enabled under `bypass` in the config (default: op)
- `gamemodeinventories.admin` - Change config options via `/gmi` (default: op)

### Compilation and Build

This project uses Gradle. To build the plugin:

```bash
./gradlew build
```

This produces the plugin jar in the `build/libs` directory. It is normally built as a submodule of Spawn-OG, which compiles against its API.

### License

GPL v3 or later. Inventory serialization derives from work by eccentric_nz, drtshock, and Kristian S. Stangeland (aadnk); the bundled `JSON` package is JSON.org code under its own license.
