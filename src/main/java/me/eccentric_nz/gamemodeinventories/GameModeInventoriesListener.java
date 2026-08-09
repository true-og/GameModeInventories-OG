package me.eccentric_nz.gamemodeinventories;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class GameModeInventoriesListener implements Listener {

    private final GameModeInventories plugin;
    private final List<Material> containers = new ArrayList<>();
    private final Set<UUID> protectedFromForcedFall = new HashSet<>();

    public GameModeInventoriesListener(GameModeInventories plugin) {

        this.plugin = plugin;
        for (String m : this.plugin.getConfig().getStringList("containers")) {

            try {

                containers.add(Material.valueOf(m));

            } catch (IllegalArgumentException e) {

                plugin.getLogger().log(Level.INFO, "Illegal material name " + m + " in containers list!");

            }

        }

    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {

        Player p = event.getPlayer();
        GameMode newGM = event.getNewGameMode();
        // Internal switches (/gmic, the policy's changeGameMode) are
        // pre-validated by their callers and trusted here; only external
        // routes (/gamemode, other plugins) are policed and told why.
        boolean internal = plugin.isInternalGameModeChange(p);
        if (!internal && newGM.equals(GameMode.CREATIVE) && !canUseCreativeAt(p, p.getLocation())) {

            // cancelling leaves them in the mode they were already in
            event.setCancelled(true);
            plugin.message(p, plugin.getM().get("NO_CREATIVE_REGION"));
            return;

        }

        if (!internal && newGM.equals(GameMode.SPECTATOR) && !plugin.getGameModePolicy().mayUseSpectator(p)) {

            event.setCancelled(true);
            plugin.message(p, plugin.getM().get("NO_SPECTATOR"));
            return;

        }

        // Cancelled rather than corrected, so adventure cannot stick in a world that
        // is meant to stay survival.
        if (!internal && newGM.equals(GameMode.ADVENTURE)
                && !plugin.getGameModePolicy().mayUseAdventureAt(p.getLocation()))
        {

            event.setCancelled(true);
            plugin.message(p, plugin.getM().get("NO_ADVENTURE"));
            return;

        }

        if (p.hasPermission("gamemodeinventories.use")) {

            if (p.isOnline()) {

                plugin.getInventoryHandler().switchInventories(p, newGM);
                if (newGM.equals(GameMode.CREATIVE) && plugin.getConfig().getBoolean("creative_world.switch_to")) {

                    Location loc = plugin.getServer().getWorld(plugin.getConfig().getString("creative_world.world"))
                            .getSpawnLocation();
                    if (plugin.getConfig().getString("creative_world.location").equals("last_known")) {

                        String uuid = p.getUniqueId().toString();
                        try (Connection connection = plugin.getDatabaseConnection();
                                PreparedStatement statement = connection.prepareStatement(
                                        "SELECT * FROM " + plugin.getPrefix() + "worlds WHERE uuid = ? AND world = ?");)
                        {

                            statement.setString(1, uuid);
                            statement.setString(2, plugin.getConfig().getString("creative_world.world"));
                            try (ResultSet rs = statement.executeQuery();) {

                                if (rs.next()) {

                                    World w = plugin.getServer().getWorld(rs.getString("world"));
                                    if (w != null) {

                                        double x = rs.getDouble("x");
                                        double y = rs.getDouble("y");
                                        double z = rs.getDouble("z");
                                        float yaw = rs.getFloat("yaw");
                                        float pitch = rs.getFloat("pitch");
                                        loc = new Location(w, x, y, z, yaw, pitch);

                                    }

                                }

                            }

                        } catch (SQLException e) {

                            plugin.debug("Could not get creative world location, " + e);

                        }

                    }

                    if (loc != null) {

                        p.teleport(loc);

                    }

                }

            }

        }

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {

        Location to = event.getTo();
        if (to == null) {

            return;

        }

        Player player = event.getPlayer();
        if (protectedFromForcedFall.contains(player.getUniqueId()) && player.isOnGround()) {

            protectedFromForcedFall.remove(player.getUniqueId());

        }

        Location from = event.getFrom();
        if (player.getGameMode().equals(GameMode.CREATIVE) && (from.getBlockX() != to.getBlockX()
                || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ())
                && !canUseCreativeAt(player, to))
        {

            forceSurvival(player);

        }

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleportCreativeRestriction(PlayerTeleportEvent event) {

        Player player = event.getPlayer();
        Location to = event.getTo();
        if (to != null && player.getGameMode().equals(GameMode.CREATIVE) && !canUseCreativeAt(player, to)) {

            plugin.getServer().getScheduler().runTask(plugin, () -> forceSurvival(player));

        }

    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {

        // Spawn-OG normalizes logins inside a safety transaction that also relocates
        // the player. Forcing survival underneath it would race that transaction, so
        // this handler only covers servers running without it.
        if (plugin.getServer().getPluginManager().isPluginEnabled("Spawn-OG")) {

            return;

        }

        Player player = event.getPlayer();
        if (player.getGameMode().equals(GameMode.CREATIVE) && !canUseCreativeAt(player, player.getLocation())) {

            plugin.getServer().getScheduler().runTask(plugin, () -> forceSurvival(player));

        }

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {

        if (event.getCause().equals(EntityDamageEvent.DamageCause.FALL) && event.getEntity() instanceof Player player
                && protectedFromForcedFall.remove(player.getUniqueId()))
        {

            event.setCancelled(true);
            player.setFallDistance(0.0F);

        }

    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {

        protectedFromForcedFall.remove(event.getPlayer().getUniqueId());

    }

    // Say why when another plugin drops a player out of Creative.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onForcedSurvival(PlayerGameModeChangeEvent event) {

        final Player p = event.getPlayer();
        // at MONITOR the change is not applied, so getGameMode() is the old mode
        if (!event.getNewGameMode().equals(GameMode.SURVIVAL) || !p.getGameMode().equals(GameMode.CREATIVE)) {

            return;

        }

        // only plugin driven Creative removal counts, not /gamemode
        if (!event.getCause().equals(PlayerGameModeChangeEvent.Cause.PLUGIN) || plugin.isInternalGameModeChange(p)) {

            return;

        }

        if (p.hasPermission("gamemodeinventories.toggle") || p.hasPermission("gamemodeinventories.anywhere")) {

            return;

        }

        plugin.message(p, plugin.getM().get("FORCED_SURVIVAL"));

    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {

        if (plugin.getConfig().getBoolean("restrict_creative")) {

            Block b = event.getClickedBlock();
            if (b != null) {

                Player p = event.getPlayer();
                if (p.isSneaking() && isBlock(p.getInventory().getItemInMainHand().getType())) {

                    return;

                }

                Material m = b.getType();
                GameMode gm = p.getGameMode();
                if (gm.equals(GameMode.CREATIVE) && containers.contains(m)
                        && !GameModeInventoriesBypass.canBypass(p, "inventories", plugin)
                        && event.getAction().equals(Action.RIGHT_CLICK_BLOCK))
                {

                    event.setCancelled(true);
                    if (!plugin.getConfig().getBoolean("dont_spam_chat")) {

                        plugin.message(p, plugin.getM().get("NO_CREATIVE_INVENTORY"));

                    }

                }

            }

        }

    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {

        if (plugin.getConfig().getBoolean("no_drops")) {

            Inventory inv = event.getInventory();
            if (inv.getType().equals(InventoryType.WORKBENCH)) {

                Player p = (Player) event.getPlayer();
                if (p.getGameMode().equals(GameMode.CREATIVE)
                        && !GameModeInventoriesBypass.canBypass(p, "inventories", plugin))
                {

                    boolean empty = true;
                    for (ItemStack is : inv.getContents()) {

                        if (!is.getType().isAir()) {

                            empty = false;

                        }

                    }

                    if (!empty) {

                        inv.clear();
                        if (!plugin.getConfig().getBoolean("dont_spam_chat")) {

                            plugin.message(p, plugin.getM().get("NO_WORKBENCH_DROPS"));

                        }

                    }

                }

            }

        }

    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent event) {

        if (plugin.getConfig().getBoolean("restrict_creative")) {

            Entity entity = event.getRightClicked();
            Player p = event.getPlayer();
            if (p.getGameMode().equals(GameMode.CREATIVE) && plugin.getInventoryHandler().isInstanceOf(entity)
                    && !GameModeInventoriesBypass.canBypass(p, "inventories", plugin))
            {

                if (!plugin.getConfig().getBoolean("dont_spam_chat")) {

                    plugin.message(p, plugin.getM().get("NO_CREATIVE_INVENTORY"));

                }

                event.setCancelled(true);

            }

        }

    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {

        onEntityClick(event);

    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {

        if (plugin.getConfig().getBoolean("no_drops")) {

            Player p = event.getPlayer();
            GameMode gm = p.getGameMode();
            if (gm.equals(GameMode.CREATIVE) && !GameModeInventoriesBypass.canBypass(p, "items", plugin)) {

                event.setCancelled(true);
                if (!plugin.getConfig().getBoolean("dont_spam_chat")) {

                    plugin.message(p, plugin.getM().get("NO_PLAYER_DROPS"));

                }

            }

        }

    }

    @EventHandler(ignoreCancelled = true)
    public void noPickup(EntityPickupItemEvent event) {

        if (event.getEntity() instanceof Player player && plugin.getConfig().getBoolean("no_pickups")) {

            GameMode gm = player.getGameMode();
            if (gm.equals(GameMode.CREATIVE) && !GameModeInventoriesBypass.canBypass(player, "items", plugin)) {

                event.setCancelled(true);
                if (!plugin.getConfig().getBoolean("dont_spam_chat")) {

                    plugin.message(player, plugin.getM().get("NO_CREATIVE_PICKUP"));

                }

            }

        }

    }

    @EventHandler(ignoreCancelled = true)
    public void noHorseInventory(InventoryOpenEvent event) {

        if (plugin.getConfig().getBoolean("restrict_creative")
                && plugin.getInventoryHandler().isInstanceOf(event.getInventory().getHolder()))
        {

            Player p = (Player) event.getPlayer();
            GameMode gm = p.getGameMode();
            if (gm.equals(GameMode.CREATIVE) && !GameModeInventoriesBypass.canBypass(p, "inventories", plugin)) {

                event.setCancelled(true);
                if (!plugin.getConfig().getBoolean("dont_spam_chat")) {

                    plugin.message(p, plugin.getM().get("NO_CREATIVE_HORSE"));

                }

            }

        }

    }

    // Both region and permission rules live in GameModePolicy so this plugin and
    // its consumers cannot drift apart on where creative is allowed.
    private boolean canUseCreativeAt(Player player, Location location) {

        return plugin.getGameModePolicy().mayUseCreativeAt(player, location);

    }

    private void forceSurvival(Player player) {

        // the anywhere permission keeps a player in whatever mode they chose
        if (!player.isOnline() || player.getGameMode().equals(GameMode.SURVIVAL)
                || player.hasPermission("gamemodeinventories.anywhere"))
        {

            return;

        }

        protectedFromForcedFall.add(player.getUniqueId());
        player.setFallDistance(0.0F);
        // flagged as plugin driven so the generic notice does not double up
        plugin.internalGameModeChange(player, GameMode.SURVIVAL);
        plugin.message(player, plugin.getM().get("FORCED_SURVIVAL"));
        plugin.getServer().getScheduler().runTask(plugin, () -> {

            if (player.isOnline() && player.isOnGround()) {

                protectedFromForcedFall.remove(player.getUniqueId());

            }

        });

    }

    private boolean isBlock(Material m) {

        return !m.isAir() && m.isBlock();

    }

}
