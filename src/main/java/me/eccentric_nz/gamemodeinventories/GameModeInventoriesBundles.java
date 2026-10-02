/*
 *  Copyright 2014 eccentric_nz.
 */
package me.eccentric_nz.gamemodeinventories;

import org.bukkit.World;

// Names a world's inventory bundle: its MyWorlds group, else the world itself
public final class GameModeInventoriesBundles {

    private final boolean myWorlds;

    public GameModeInventoriesBundles(GameModeInventories plugin) {

        myWorlds = plugin.getServer().getPluginManager().isPluginEnabled("MyWorlds") && MyWorldsBundles.available();
        plugin.getLogger().info("[GameModeInventories] Inventory bundles: "
                + (myWorlds ? "MyWorlds inventory groups" : "one per world"));

    }

    public boolean usesMyWorlds() {

        return myWorlds;

    }

    public String of(World world) {

        if (myWorlds) {

            String group = MyWorldsBundles.of(world);
            if (group != null) {

                return group;

            }

        }

        return world.getName();

    }

}
