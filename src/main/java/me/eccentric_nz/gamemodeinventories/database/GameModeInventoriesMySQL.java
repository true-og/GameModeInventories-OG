/*
 *  Copyright 2014 eccentric_nz.
 */
package me.eccentric_nz.gamemodeinventories.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import me.eccentric_nz.gamemodeinventories.GameModeInventories;

public class GameModeInventoriesMySQL {

    private final GameModeInventories plugin;

    public GameModeInventoriesMySQL(GameModeInventories plugin) {

        this.plugin = plugin;

    }

    public void createTables() {

        try (Connection connection = plugin.getDatabaseConnection();
                Statement statement = connection.createStatement();)
        {

            // add inventories table
            String queryInventories = "CREATE TABLE IF NOT EXISTS " + plugin.getPrefix()
                    + "inventories (id int(11) NOT NULL AUTO_INCREMENT, uuid varchar(48) DEFAULT '', player varchar(24) DEFAULT '', gamemode varchar(24) DEFAULT '', inventory text, xp double, armour text, enderchest text, attributes text, armour_attributes text, bundle varchar(64), PRIMARY KEY (id)) DEFAULT CHARSET=utf8 COLLATE utf8_general_ci";
            statement.executeUpdate(queryInventories);
            // deaths table, parked until the player is back in the death world
            String queryDeaths = "CREATE TABLE IF NOT EXISTS " + plugin.getPrefix()
                    + "deaths (id int(11) NOT NULL AUTO_INCREMENT, uuid varchar(48) DEFAULT '', player varchar(24) DEFAULT '', gamemode varchar(24) DEFAULT '', world varchar(64), inventory text, armour text, PRIMARY KEY (id)) DEFAULT CHARSET=utf8 COLLATE utf8_general_ci";
            statement.executeUpdate(queryDeaths);

            // add the bundle column, the inventory group a slot belongs to
            String queryBundle = "SHOW COLUMNS FROM " + plugin.getPrefix() + "inventories LIKE 'bundle'";
            try (ResultSet rsBundle = statement.executeQuery(queryBundle);) {

                if (!rsBundle.next()) {

                    statement.executeUpdate("ALTER TABLE " + plugin.getPrefix() + "inventories ADD bundle varchar(64)");
                    plugin.getLogger().log(Level.INFO, "[GameModeInventories] Adding bundle to database!");

                }

            }

            // One slot per (uuid, gamemode, bundle); stops a failed legacy assignment
            // leaving conflicting rows
            String queryUnique = "SHOW INDEX FROM " + plugin.getPrefix() + "inventories WHERE Key_name = 'uq_gmi_slot'";
            try (ResultSet rsUnique = statement.executeQuery(queryUnique);) {

                if (!rsUnique.next()) {

                    statement.executeUpdate("ALTER TABLE " + plugin.getPrefix()
                            + "inventories ADD UNIQUE KEY uq_gmi_slot (uuid, gamemode, bundle)");

                }

            }

            // update inventories if there is no attributes column
            String queryAttr = "SHOW COLUMNS FROM " + plugin.getPrefix() + "inventories LIKE 'attributes'";
            try (ResultSet rsAttr = statement.executeQuery(queryAttr);) {

                if (!rsAttr.next()) {

                    String queryAlter4 = "ALTER TABLE " + plugin.getPrefix() + "inventories ADD attributes text";
                    statement.executeUpdate(queryAlter4);
                    String queryAlter5 = "ALTER TABLE " + plugin.getPrefix() + "inventories ADD armour_attributes text";
                    statement.executeUpdate(queryAlter5);
                    plugin.getLogger().log(Level.INFO, "[GameModeInventories] Adding attributes to database!");

                }

            }

            // add blocks table
            String queryBlocks = "CREATE TABLE IF NOT EXISTS " + plugin.getPrefix()
                    + "blocks (id int(11) NOT NULL AUTO_INCREMENT, worldchunk varchar(128), location text, PRIMARY KEY (id)) DEFAULT CHARSET=utf8 COLLATE utf8_general_ci";
            statement.executeUpdate(queryBlocks);
            // update blocks if there is no world column
            String queryWorld = "SHOW COLUMNS FROM " + plugin.getPrefix() + "blocks LIKE 'worldchunk'";
            try (ResultSet rsWorld = statement.executeQuery(queryWorld);) {

                if (!rsWorld.next()) {

                    String queryAlter6 = "ALTER TABLE " + plugin.getPrefix() + "blocks ADD worldchunk varchar(128)";
                    statement.executeUpdate(queryAlter6);
                    plugin.getLogger().log(Level.INFO, "[GameModeInventories] Adding new fields to database!");

                }

            }

            // add stands table
            String queryStands = "CREATE TABLE IF NOT EXISTS " + plugin.getPrefix()
                    + "stands (uuid varchar(48) NOT NULL, PRIMARY KEY (uuid)) DEFAULT CHARSET=utf8 COLLATE utf8_general_ci";
            statement.executeUpdate(queryStands);
            // add worlds table
            String queryWorlds = "CREATE TABLE IF NOT EXISTS " + plugin.getPrefix()
                    + "worlds (id int(11) NOT NULL AUTO_INCREMENT, uuid varchar(48) DEFAULT '', world varchar(24) DEFAULT '', x double, y double, z double, yaw float, pitch float, PRIMARY KEY (id)) DEFAULT CHARSET=utf8 COLLATE utf8_general_ci";
            statement.executeUpdate(queryWorlds);

        } catch (SQLException e) {

            plugin.getLogger().log(Level.WARNING, "MySQL create table error: " + e);

        }

    }

}
