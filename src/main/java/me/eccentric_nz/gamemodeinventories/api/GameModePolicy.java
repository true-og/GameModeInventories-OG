package me.eccentric_nz.gamemodeinventories.api;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import me.eccentric_nz.gamemodeinventories.GameModeInventories;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

// The single authority on which gamemodes GameModeInventories-OG sanctions and where; published so other
// plugins enforce the same rule. Obtain via the Bukkit services manager or GameModeInventories#getGameModePolicy().
public final class GameModePolicy {

    public static final String ANYWHERE_PERMISSION = "gamemodeinventories.anywhere";
    public static final String TOGGLE_PERMISSION = "gamemodeinventories.toggle";
    public static final String SPECTATOR_PERMISSION = "gamemodeinventories.spectator";
    public static final String NOCLIP_PERMISSION = "noclip.use";

    private static final List<String> DEFAULT_CREATIVE_REGIONS = List.of("spawn");

    private final GameModeInventories plugin;
    private final RegionContainer regionContainer;
    private final Set<String> creativeRegions = new HashSet<>();

    public GameModePolicy(GameModeInventories plugin) {

        this.plugin = plugin;
        this.regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();

        List<String> configured = plugin.getConfig().getStringList("creative_regions");
        if (configured.isEmpty()) {

            configured = DEFAULT_CREATIVE_REGIONS;

        }

        configured.stream().map(region -> region.toLowerCase(Locale.ROOT)).forEach(creativeRegions::add);

    }

    // Whether this plugin's rules permit the player to hold the gamemode at the
    // location. Survival is always permitted; the rest follow the rules below.
    public boolean mayUse(Player player, GameMode gameMode, Location location) {

        return switch (gameMode) {

            case SURVIVAL -> true;
            case CREATIVE -> mayUseCreativeAt(player, location);
            case SPECTATOR -> mayUseSpectator(player);
            case ADVENTURE -> mayUseAdventureAt(location);

        };

    }

    // Minigames put players in adventure legitimately, so it is refused only in
    // worlds meant to stay survival; minigame worlds must be left off that list.
    public boolean mayUseAdventureAt(Location location) {

        if (location == null || location.getWorld() == null)
            return true;

        String world = location.getWorld().getName();
        return plugin.getConfig().getStringList("restrict_adventure_worlds").stream()
                .noneMatch(restricted -> restricted.equalsIgnoreCase(world));

    }

    // Creative is staff only. The permission says who, the region says where,
    // and both are required: a region alone licenses no ordinary player.
    public boolean mayUseCreativeAt(Player player, Location location) {

        if (player.hasPermission(ANYWHERE_PERMISSION))
            return true;
        if (!player.hasPermission(TOGGLE_PERMISSION))
            return false;
        if (location == null || location.getWorld() == null)
            return false;

        return regionContainer.createQuery().getApplicableRegions(BukkitAdapter.adapt(location)).getRegions().stream()
                .anyMatch(region -> creativeRegions.contains(region.getId().toLowerCase(Locale.ROOT)));

    }

    // Spectator is permission gated rather than region gated, and only while
    // restrict_spectator is on.
    public boolean mayUseSpectator(Player player) {

        return !plugin.getConfig().getBoolean("restrict_spectator") || player.hasPermission(SPECTATOR_PERMISSION)
                || player.hasPermission(NOCLIP_PERMISSION);

    }

    // Lower-case ids of the regions creative is allowed in.
    public Set<String> getCreativeRegions() {

        return Set.copyOf(creativeRegions);

    }

    // The sanctioned programmatic switch: policy-validated, then flagged internal
    // so the listener trusts it. False: refused here or cancelled elsewhere.
    public boolean changeGameMode(Player player, GameMode gameMode) {

        if (!mayUse(player, gameMode, player.getLocation()))
            return false;

        plugin.internalGameModeChange(player, gameMode);
        return player.getGameMode() == gameMode;

    }

}
