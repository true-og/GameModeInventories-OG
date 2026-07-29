## GameModeInventories-OG

Allow players to have separate inventories for each game mode (Creative, Survival and Adventure).

This is a [TrueOG Network](https://trueog.net) maintained soft fork of [eccentricdevotion/GameModeInventories](https://github.com/eccentricdevotion/GameModeInventories).

### Changes from Upstream

- Build system migrated from Maven to Gradle (Kotlin DSL), Gradle 8.14.3, Shadow 8.3.9, Spotless with TrueOG Network's eclipse-based Java Formatting.
- `config.yml` is read-only at runtime — the plugin no longer mutates or rewrites it. A default `config.yml` is copied into the plugin folder on first run if missing.
- Compatible with [NoClip-OG](https://github.com/true-og/NoClip-OG) (1.3.0+). When `restrict_spectator` is enabled, players holding the `noclip.use` permission are exempt from the restriction so NoClip's creative/spectator toggle works without being cancelled or spamming chat.
- Creative is staff only: it needs `gamemodeinventories.toggle` *and* a region in `creative_regions`, or `gamemodeinventories.anywhere` to ignore both. Spectator needs `gamemodeinventories.spectator` or `noclip.use`. Adventure is refused in `restrict_adventure_worlds`.
- Gamemode rules live in one place, `api/GameModePolicy`, which every enforcement point in the plugin asks. It is published for other plugins so they enforce the same rule instead of reimplementing it: fetch it from the Bukkit services manager, or reflectively via `GameModeInventories#getGameModePolicy()`. `mayUse(player, gameMode, location)` answers for any gamemode; survival is always allowed, adventure never is.
- Login is deferred to [Spawn-OG](https://github.com/true-og/Spawn-OG) when it is installed. It normalizes gamemode inside a safety transaction that also relocates the player, so forcing survival underneath it would race that transaction. Without Spawn-OG, GameModeInventories-OG still forces survival on join for creative players outside a creative region.
- Dropped LogBlock integration. Block-removal logging now runs exclusively through CoreProtect / CoreProtect-OG.
- Targets Purpur API 1.19.4 (Java 17, GraalVM toolchain).

This plugin (and the GMIDatabaseConverter plugin) are available for download as a single ZIP file from the [GameModeInventories page on BukkitDev](http://dev.bukkit.org/bukkit-plugins/gamemodeinventories).

### Warning

This version of GameModeInventories uses a different storage format when saving inventories from the legacy one. Before installing this version, you should first run the [GMIDatabaseConverter](https://github.com/eccentricdevotion/GMIDatabaseConverter/blob/master/README.md) plugin on your CraftBukkit 1.6.4 server to update your GameModeInventories database.

### How do I update my GMI database?

**_Before_** upgrading your server to CraftBukkit 1.7.x and installing GameModeInventories version 2.x, you should run GMIDatabaseConverter on your **1.6.4** server.

1. Install GMIDatabaseConverter.jar to the server's plugins folder
2. Start the server
   * The plugin will attempt to find and backup your old GameModeInventories database file
   * It will then read the existing inventory data
   * The existing data will be converted to the new format
   * The new data will be written back to the database
3. Once conversion is complete, you can update your GameModeInventories plugin to version 2.x and restart the server
4. If you are satisfied that GameModeInventories version 2.x is functioning correctly, you can safely remove GMIDatabaseConverter


### Why did you change the storage format?

The format change is a result of code changes removing the reliance on using net.minecraft.server and org.craftbukkit code directly within the plugin (instead of using only the Bukkit API). This led to the plugin breaking with every Minecraft/CraftBukkit update. These code changes mean the plugin should no longer break between versions.
