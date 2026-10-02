/*
 * Kristian S. Stangeland aadnk
 * Norway
 * kristian@comphenix.net
 * thtp://www.comphenix.net/
 */
package me.eccentric_nz.gamemodeinventories;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.bukkit.GameMode;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.minecart.HopperMinecart;
import org.bukkit.entity.minecart.PoweredMinecart;
import org.bukkit.entity.minecart.StorageMinecart;
import org.bukkit.inventory.InventoryHolder;

// Owns only the CREATIVE inventory per bundle. Survival lives in Purpur's RocksDB through MyWorlds, so no
// inventory is ever stored in two places and the two stores cannot diverge.
public class GameModeInventoriesInventory {

    private static final String CREATIVE = GameMode.CREATIVE.name();

    private final GameModeInventories plugin;
    private final boolean saveXP;
    private final boolean saveArmour;
    private final boolean saveEnderChest;
    private final boolean potions;

    public GameModeInventoriesInventory(GameModeInventories plugin) {

        this.plugin = plugin;
        saveXP = this.plugin.getConfig().getBoolean("xp");
        saveArmour = this.plugin.getConfig().getBoolean("armor");
        saveEnderChest = this.plugin.getConfig().getBoolean("enderchest");
        potions = this.plugin.getConfig().getBoolean("remove_potions");

    }

    // Persists the player's current (creative) inventory for this bundle. Kicks on
    // failure so an unsaved
    // creative inventory is never silently dropped or overwritten later.
    public void saveCreative(Player player, String bundle) {

        String uuid = player.getUniqueId().toString();
        String name = player.getName();
        GameModeInventoriesXPCalculator xpc = saveXP ? new GameModeInventoriesXPCalculator(player) : null;
        String inv = GameModeInventoriesBukkitSerialization.toDatabase(player.getInventory().getContents());
        int xp = saveXP ? xpc.getCurrentExp() : 0;
        String arm = saveArmour
                ? GameModeInventoriesBukkitSerialization.toDatabase(player.getInventory().getArmorContents())
                : null;
        String ender = (saveEnderChest && player.getEnderChest() != null)
                ? GameModeInventoriesBukkitSerialization.toDatabase(player.getEnderChest().getContents())
                : null;

        try (Connection connection = plugin.getDatabaseConnection()) {

            connection.setAutoCommit(false);
            try {

                saveSlot(connection, uuid, name, CREATIVE, bundle, inv, xp, arm, ender);
                connection.commit();

            } catch (Exception e) {

                connection.rollback();
                throw e;

            } finally {

                connection.setAutoCommit(true);

            }

        } catch (Exception e) {

            GameModeInventories.plugin.debug("Could not save creative inventory, kicking to avoid losing it, " + e);
            player.kickPlayer("Inventory sync failed, please rejoin");

        }

    }

    // Loads the player's creative inventory for this bundle, or clears it when none
    // is stored. Kicks on failure.
    public void loadCreative(Player player, String bundle) {

        String uuid = player.getUniqueId().toString();
        GameModeInventoriesXPCalculator xpc = saveXP ? new GameModeInventoriesXPCalculator(player) : null;
        try (Connection connection = plugin.getDatabaseConnection()) {

            LoadedSlot dest = readSlot(connection, uuid, CREATIVE, bundle);
            applyCreative(player, dest, xpc);

        } catch (Exception e) {

            GameModeInventories.plugin
                    .debug("Could not load creative inventory, kicking to avoid a wrong profile, " + e);
            player.kickPlayer("Inventory sync failed, please rejoin");

        }

    }

    // Removes potion effects the player picked up in creative, matching the old
    // leave-creative behavior.
    public void clearCreativePotions(Player player) {

        if (potions) {

            player.getActivePotionEffects().forEach((effect) -> player.removePotionEffect(effect.getType()));

        }

    }

    // The decoded contents of one (uuid, gamemode, bundle) slot, or null when none
    // is stored.
    private static final class LoadedSlot {

        final ItemStack[] inv;
        final ItemStack[] arm;
        final ItemStack[] ender;
        final int xp;

        LoadedSlot(ItemStack[] inv, ItemStack[] arm, ItemStack[] ender, int xp) {

            this.inv = inv;
            this.arm = arm;
            this.ender = ender;
            this.xp = xp;

        }

    }

    private static ItemStack[] decode(String s) throws IOException {

        return s.startsWith("[") ? GameModeInventoriesJSONSerialization.toItemStacks(s)
                : GameModeInventoriesBukkitSerialization.fromDatabase(s);

    }

    // Reads and fully decodes a slot before anything is applied. Null when the
    // player has none yet.
    private LoadedSlot readSlot(Connection connection, String uuid, String gamemode, String bundle)
            throws SQLException, IOException
    {

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM " + plugin.getPrefix() + "inventories WHERE uuid = ? AND gamemode = ? AND bundle = ?");)
        {

            statement.setString(1, uuid);
            statement.setString(2, gamemode);
            statement.setString(3, bundle);
            try (ResultSet rs = statement.executeQuery();) {

                if (!rs.next()) {

                    return null;

                }

                ItemStack[] inv = decode(rs.getString("inventory"));
                int xp = rs.getInt("xp");
                ItemStack[] arm = null;
                if (saveArmour) {

                    String savedarmour = rs.getString("armour");
                    if (savedarmour != null) {

                        arm = decode(savedarmour);

                    }

                }

                ItemStack[] ender = null;
                if (saveEnderChest) {

                    String savedender = rs.getString("enderchest");
                    if (savedender == null || savedender.equals("[Null]") || savedender.isEmpty()) {

                        savedender = "[\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\",\"null\"]";

                    }

                    ender = decode(savedender);

                }

                return new LoadedSlot(inv, arm, ender, xp);

            }

        }

    }

    // Upserts one (uuid, gamemode, bundle) slot with the given contents.
    private void saveSlot(Connection connection, String uuid, String name, String gamemode, String bundle, String inv,
            int xp, String arm, String ender) throws SQLException
    {

        int id;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT id FROM " + plugin.getPrefix() + "inventories WHERE uuid = ? AND gamemode = ? AND bundle = ?");)
        {

            select.setString(1, uuid);
            select.setString(2, gamemode);
            select.setString(3, bundle);
            try (ResultSet rs = select.executeQuery();) {

                if (rs.next()) {

                    id = rs.getInt("id");
                    try (PreparedStatement ps = connection.prepareStatement(
                            "UPDATE " + plugin.getPrefix() + "inventories SET inventory = ? WHERE id = ?");)
                    {

                        ps.setString(1, inv);
                        ps.setInt(2, id);
                        ps.executeUpdate();

                    }

                } else {

                    try (PreparedStatement ps = connection.prepareStatement(
                            "INSERT INTO " + plugin.getPrefix()
                                    + "inventories (uuid, player, gamemode, inventory, bundle) VALUES (?, ?, ?, ?, ?)",
                            PreparedStatement.RETURN_GENERATED_KEYS);)
                    {

                        ps.setString(1, uuid);
                        ps.setString(2, name);
                        ps.setString(3, gamemode);
                        ps.setString(4, inv);
                        ps.setString(5, bundle);
                        ps.executeUpdate();
                        try (ResultSet keys = ps.getGeneratedKeys();) {

                            id = keys.next() ? keys.getInt(1) : 0;

                        }

                    }

                }

            }

        }

        if (saveXP) {

            try (PreparedStatement ps = connection
                    .prepareStatement("UPDATE " + plugin.getPrefix() + "inventories SET xp = ? WHERE id = ?");)
            {

                ps.setInt(1, xp);
                ps.setInt(2, id);
                ps.executeUpdate();

            }

        }

        if (saveArmour && arm != null) {

            try (PreparedStatement ps = connection
                    .prepareStatement("UPDATE " + plugin.getPrefix() + "inventories SET armour = ? WHERE id = ?");)
            {

                ps.setString(1, arm);
                ps.setInt(2, id);
                ps.executeUpdate();

            }

        }

        if (saveEnderChest && ender != null) {

            try (PreparedStatement ps = connection
                    .prepareStatement("UPDATE " + plugin.getPrefix() + "inventories SET enderchest = ? WHERE id = ?");)
            {

                ps.setString(1, ender);
                ps.setInt(2, id);
                ps.executeUpdate();

            }

        }

    }

    // Puts the decoded creative slot onto the player, or clears everything when
    // there was none.
    private void applyCreative(Player player, LoadedSlot dest, GameModeInventoriesXPCalculator xpc) {

        if (dest == null) {

            player.getInventory().clear();
            if (saveArmour) {

                player.getInventory().setBoots(null);
                player.getInventory().setChestplate(null);
                player.getInventory().setLeggings(null);
                player.getInventory().setHelmet(null);

            }

            if (saveEnderChest) {

                player.getEnderChest().clear();

            }

            if (saveXP) {

                xpc.setExp(0);

            }

            player.updateInventory();
            return;

        }

        player.getInventory().setContents(dest.inv);
        if (saveArmour && dest.arm != null) {

            player.getInventory().setArmorContents(dest.arm);

        }

        if (saveEnderChest && dest.ender != null) {

            player.getEnderChest().setContents(dest.ender);

        }

        if (saveXP) {

            xpc.setExp(dest.xp);

        }

        player.updateInventory();

    }

    // Pre-bundle slots were used everywhere, so they join the main bundle. False
    // when the migration could not run.
    public boolean assignLegacySlotsTo(String bundle) {

        try (Connection connection = plugin.getDatabaseConnection();
                PreparedStatement ps = connection.prepareStatement(
                        "UPDATE " + plugin.getPrefix() + "inventories SET bundle = ? WHERE bundle IS NULL");)
        {

            ps.setString(1, bundle);
            int moved = ps.executeUpdate();
            if (moved > 0) {

                plugin.getLogger().info(
                        "[GameModeInventories] Assigned " + moved + " legacy inventory slots to bundle " + bundle);

            }

            return true;

        } catch (SQLException e) {

            GameModeInventories.plugin.debug("Could not assign legacy inventory slots to a bundle, " + e);
            return false;

        }

    }

    // Parks a death's inventory under its bundle and mode. Always inserts, so an
    // earlier unrestored death is never
    // overwritten. Returns true only when the row is durably written, so the caller
    // can keep vanilla drops otherwise.
    public boolean saveOnDeath(Player p) {

        String uuid = p.getUniqueId().toString();
        String name = p.getName();
        String gm = p.getGameMode().name();
        String bundle = plugin.getBundles().of(p.getWorld());
        String inv = GameModeInventoriesBukkitSerialization.toDatabase(p.getInventory().getContents());
        String arm = GameModeInventoriesBukkitSerialization.toDatabase(p.getInventory().getArmorContents());
        try (Connection connection = plugin.getDatabaseConnection();
                PreparedStatement ps = connection.prepareStatement("INSERT INTO " + plugin.getPrefix()
                        + "deaths (uuid, player, gamemode, world, inventory, armour) VALUES (?, ?, ?, ?, ?, ?)");)
        {

            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setString(3, gm);
            // The 'world' column holds the inventory bundle, so a shared group restores
            // anywhere in it
            ps.setString(4, bundle);
            ps.setString(5, inv);
            ps.setString(6, arm);
            return ps.executeUpdate() > 0;

        } catch (SQLException e) {

            GameModeInventories.plugin.debug("Could not save inventories on player death, " + e);
            return false;

        }

    }

    // Returns the oldest death parked in this bundle and mode, added to the player
    // without discarding what they hold.
    public void restoreOnSpawn(Player p) {

        String uuid = p.getUniqueId().toString();
        String gm = p.getGameMode().name();
        String bundle = plugin.getBundles().of(p.getWorld());
        try (Connection connection = plugin.getDatabaseConnection();
                PreparedStatement statement = connection.prepareStatement("SELECT * FROM " + plugin.getPrefix()
                        + "deaths WHERE uuid = ? AND world = ? AND gamemode = ? ORDER BY id LIMIT 1");)
        {

            statement.setString(1, uuid);
            statement.setString(2, bundle);
            statement.setString(3, gm);
            try (ResultSet rs = statement.executeQuery();) {

                if (!rs.next()) {

                    return;

                }

                int id = rs.getInt("id");
                ItemStack[] i;
                ItemStack[] a;
                try {

                    i = decode(rs.getString("inventory"));
                    a = decode(rs.getString("armour"));

                } catch (IOException e) {

                    GameModeInventories.plugin.debug("Could not restore inventories on respawn, " + e);
                    return;

                }

                giveOrDrop(p, i);
                giveOrDrop(p, a);

                try (PreparedStatement clear = connection
                        .prepareStatement("DELETE FROM " + plugin.getPrefix() + "deaths WHERE id = ?");)
                {

                    clear.setInt(1, id);
                    clear.executeUpdate();

                }

            }

        } catch (SQLException e) {

            GameModeInventories.plugin.debug("Could not restore inventories on respawn, " + e);

        }

    }

    // Adds items to the player, dropping any that do not fit, so a restore never
    // clobbers what they already hold.
    private void giveOrDrop(Player p, ItemStack[] items) {

        if (items == null) {

            return;

        }

        for (ItemStack item : items) {

            if (item == null || item.getType().isAir()) {

                continue;

            }

            p.getInventory().addItem(item).values()
                    .forEach(leftover -> p.getWorld().dropItem(p.getLocation(), leftover));

        }

    }

    public boolean isInstanceOf(Entity e) {

        return e instanceof PoweredMinecart || e instanceof StorageMinecart || e instanceof HopperMinecart
                || e instanceof ItemFrame || e instanceof ArmorStand;

    }

    public boolean isInstanceOf(InventoryHolder h) {

        return (h instanceof AbstractHorse);

    }

}
