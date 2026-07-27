package me.eccentric_nz.gamemodeinventories;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
    private final Set<String> creativeRegions = new HashSet<>();
    private final Set<UUID> protectedFromForcedFall = new HashSet<>();
    private final RegionContainer regionContainer;

    public GameModeInventoriesListener(GameModeInventories plugin) {

        this.plugin = plugin;
        regionContainer = WorldGuard.getInstance().getPlatform().getRegionContainer();
        List<String> configuredRegions = this.plugin.getConfig().getStringList("creative_regions");
        if (configuredRegions.isEmpty()) {

            configuredRegions = List.of("spawn");

        }

        configuredRegions.stream().map(region -> region.toLowerCase(Locale.ROOT)).forEach(creativeRegions::add);
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
        if (newGM.equals(GameMode.CREATIVE) && !canUseCreativeAt(p, p.getLocation())) {

            // cancelling leaves them in the mode they were already in
            event.setCancelled(true);
            // /gmic reports its own denial, so only speak for other routes into Creative
            if (!plugin.isInternalGameModeChange(p)) {

                plugin.message(p, plugin.getM().get("NO_CREATIVE_REGION"));

            }

            return;

        }

        if (newGM.equals(GameMode.SPECTATOR) && plugin.getConfig().getBoolean("restrict_spectator")
                && !p.hasPermission("gamemodeinventories.spectator") && !p.hasPermission("noclip.use"))
        {

            event.setCancelled(true);
            plugin.message(p, plugin.getM().get("NO_SPECTATOR"));
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

    private boolean canUseCreativeAt(Player player, Location location) {

        if (player.hasPermission("gamemodeinventories.anywhere")) {

            return true;

        }

        ApplicableRegionSet regions = regionContainer.createQuery().getApplicableRegions(BukkitAdapter.adapt(location));
        return regions.getRegions().stream()
                .anyMatch(region -> creativeRegions.contains(region.getId().toLowerCase(Locale.ROOT)));

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
