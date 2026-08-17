/*
 *  Copyright 2014 eccentric_nz.
 */
package me.eccentric_nz.gamemodeinventories;

import java.util.logging.Level;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.PluginManager;

public class GameModeInventoriesBlockLogger {

    private final GameModeInventories plugin;
    private CoreProtectAPI coreProtectAPI = null;
    private boolean logging = false;

    public GameModeInventoriesBlockLogger(GameModeInventories plugin) {

        this.plugin = plugin;

    }

    public CoreProtectAPI getCoreProtectAPI() {

        return coreProtectAPI;

    }

    public boolean isLogging() {

        return logging;

    }

    public void enableLogger() {

        PluginManager pm = plugin.getServer().getPluginManager();
        if (pm.isPluginEnabled("CoreProtect")) {

            CoreProtect cp = (CoreProtect) pm.getPlugin("CoreProtect");
            if (cp == null) {

                return;

            }

            CoreProtectAPI api = cp.getAPI();
            if (!api.isEnabled()) {

                return;

            }

            if (api.APIVersion() < 6) {

                return;

            }

            plugin.getLogger().log(Level.INFO, "Connecting to CoreProtect");
            coreProtectAPI = api;
            logging = true;

        }

    }

}
