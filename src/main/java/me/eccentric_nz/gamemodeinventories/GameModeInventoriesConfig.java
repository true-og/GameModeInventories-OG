/*
 *  Copyright 2013 eccentric_nz.
 */
package me.eccentric_nz.gamemodeinventories;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

// Checks the deployed config.yml against the canonical one bundled in the jar.
// There is no defaults map: the bundled file is the only description of what
// this plugin reads, so options cannot go missing from it or drift out of step
// with a second copy in code. Nothing is written back, ever.
public class GameModeInventoriesConfig {

    private final GameModeInventories plugin;
    private final File configFile;

    public GameModeInventoriesConfig(GameModeInventories plugin) {

        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "config.yml");

    }

    public void checkConfig() {

        if (!configFile.exists()) {

            plugin.getLogger().log(Level.SEVERE, "config.yml was not found at " + configFile.getAbsolutePath()
                    + ". This plugin only reads config.yml and will not generate or modify it.");
            return;

        }

        YamlConfiguration canonical = loadCanonical();
        if (canonical == null) {

            plugin.getLogger().log(Level.SEVERE,
                    "The canonical config.yml is missing from the jar; deployed options cannot be checked.");
            return;

        }

        FileConfiguration deployed = plugin.getConfig();
        List<String> missing = canonical.getKeys(true).stream().filter(key -> !canonical.isConfigurationSection(key))
                .filter(key -> !deployed.contains(key)).toList();

        missing.forEach(key -> plugin.getLogger().log(Level.WARNING, "Missing config option: " + key));

        if (!missing.isEmpty()) {

            plugin.getLogger().log(Level.WARNING, "config.yml is immutable; " + missing.size()
                    + " option(s) are missing and were not written automatically. Copy them from the jar's config.yml.");

        }

        warnAboutLegacyOptions(deployed);

    }

    private YamlConfiguration loadCanonical() {

        try (InputStream resource = plugin.getResource("config.yml")) {

            if (resource == null) {

                return null;

            }

            return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));

        } catch (Exception error) {

            plugin.getLogger().log(Level.SEVERE, "Could not read the canonical config.yml: " + error.getMessage());
            return null;

        }

    }

    private void warnAboutLegacyOptions(FileConfiguration deployed) {

        if (deployed.getStringList("blacklist").contains("ZOMBIE_PIGMAN_SPAWN_EGG")) {

            plugin.getLogger().log(Level.WARNING,
                    "config.yml still contains ZOMBIE_PIGMAN_SPAWN_EGG; update it manually to ZOMBIFIED_PIGLIN_SPAWN_EGG if needed.");

        }

        if (deployed.contains("storage.mysql.url")) {

            plugin.getLogger().log(Level.WARNING,
                    "Legacy storage.mysql.url is still present in config.yml; update it manually because automatic migration is disabled.");

        }

    }

}
