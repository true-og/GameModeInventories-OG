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

// The single authority on which gamemodes GameModeInventories-OG sanctions, and
// where. Every enforcement point inside this plugin asks it, and it is published
// so other plugins enforce the same rule instead of reimplementing it.
//
// Obtain it from the Bukkit services manager, or reflectively through
// GameModeInventories#getGameModePolicy() when compiling against this plugin is
// not practical.
public final class GameModePolicy {

    public static final String ANYWHERE_PERMISSION = "gamemodeinventories.anywhere";
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

    // Whether this plugin's rules permit the player to be in the gamemode at the
    // location. Survival is always permitted; adventure never is, because this
    // plugin does not grant it; creative and spectator follow the rules below.
    public boolean mayUse(Player player, GameMode gameMode, Location location) {

        return switch (gameMode) {

            case SURVIVAL -> true;
            case CREATIVE -> mayUseCreativeAt(player, location);
            case SPECTATOR -> mayUseSpectator(player);
            default -> false;

        };

    }

    // Creative is allowed server wide by permission, otherwise only in a creative
    // region.
    public boolean mayUseCreativeAt(Player player, Location location) {

        if (player.hasPermission(ANYWHERE_PERMISSION))
            return true;
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

}
