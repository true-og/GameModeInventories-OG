/*
 *  Copyright 2014 eccentric_nz.
 */
package me.eccentric_nz.gamemodeinventories;

import com.bergerkiller.bukkit.mw.events.PlayerProfileAppliedEvent;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;

// The one class compiled against MyWorlds, registered only when it is present. Creative follows the bundle too,
// so a world change swaps the creative inventory just like a game mode change does.
public class GameModeInventoriesMyWorldsListener implements Listener {

    private final GameModeInventories plugin;

    public GameModeInventoriesMyWorldsListener(GameModeInventories plugin) {

        this.plugin = plugin;

    }

    // Before MyWorlds loads the destination's survival profile, save the creative
    // inventory of the world being left.
    @EventHandler(priority = EventPriority.NORMAL)
    public void onWorldChange(PlayerChangedWorldEvent event) {

        Player p = event.getPlayer();
        if (p.getGameMode().equals(GameMode.CREATIVE) && p.hasPermission("gamemodeinventories.use")) {

            plugin.getInventoryHandler().saveCreative(p, plugin.getBundles().of(event.getFrom()));

        }

    }

    // MyWorlds loads the destination survival profile at MONITOR and then fires
    // this; a creative player gets the
    // destination bundle's creative inventory loaded over it.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileApplied(PlayerProfileAppliedEvent event) {

        Player p = event.getPlayer();
        if (p.getGameMode().equals(GameMode.CREATIVE) && p.hasPermission("gamemodeinventories.use")) {

            plugin.getInventoryHandler().loadCreative(p, plugin.getBundles().of(p.getWorld()));

        }

    }

}
