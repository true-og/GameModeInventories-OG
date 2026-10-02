/*
 *  Copyright 2014 eccentric_nz.
 */
package me.eccentric_nz.gamemodeinventories;

import com.bergerkiller.bukkit.mw.WorldConfig;
import com.bergerkiller.bukkit.mw.WorldInventory;
import org.bukkit.World;

// The only class referencing MyWorlds, loaded once MyWorlds is known present
final class MyWorldsBundles {

    private MyWorldsBundles() {

    }

    static boolean available() {

        try {

            Class.forName("com.bergerkiller.bukkit.mw.WorldInventory");
            return true;

        } catch (Throwable t) {

            return false;

        }

    }

    // Storage world of the inventory group, null when MyWorlds has none
    static String of(World world) {

        try {

            WorldInventory group = WorldConfig.get(world).inventory;
            return group == null ? null : group.getSharedWorldName();

        } catch (Throwable t) {

            GameModeInventories.plugin
                    .debug("Could not resolve the MyWorlds inventory group of " + world.getName() + ", " + t);
            return null;

        }

    }

}
