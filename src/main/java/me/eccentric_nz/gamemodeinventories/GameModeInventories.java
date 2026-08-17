package me.eccentric_nz.gamemodeinventories;

import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import me.eccentric_nz.gamemodeinventories.api.GameModePolicy;
import me.eccentric_nz.gamemodeinventories.database.*;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class GameModeInventories extends JavaPlugin {

    public static GameModeInventories plugin;
    public final Component MY_PLUGIN_NAME = Component.text("[GameModeInventories] ", NamedTextColor.GOLD);
    private final HashMap<String, List<String>> creativeBlocks = new HashMap<>();
    private final List<Material> blackList = new ArrayList<>();
    private final List<Material> noTrackList = new ArrayList<>();
    private final List<String> points = new ArrayList<>();
    private final List<UUID> stands = new ArrayList<>();
    private final Set<UUID> internalSwitches = new HashSet<>();
    public BukkitTask recordingTask;
    private GameModeInventoriesInventory inventoryHandler;
    private GameModePolicy gameModePolicy;
    private GameModeInventoriesBlock block;
    private GameModeInventoriesMessage m;
    private GameModeInventoriesBlockLogger blockLogger;
    private GMIDebug db_level;
    private String prefix;
    private HikariDataSource dataSource;
    private FileConfiguration canonicalConfig;

    @Override
    public void onDisable() {

        if (inventoryHandler != null) {

            getServer().getOnlinePlayers().forEach((p) -> {

                if (p.hasPermission("gamemodeinventories.use")) {

                    if (p.isOnline()) {

                        inventoryHandler.switchInventories(p, p.getGameMode());

                    }

                }

            });

        }

        if (hasDatabaseConnection()) {

            new GameModeInventoriesStand(this).saveStands();
            new GameModeInventoriesQueueDrain(this).forceDrainQueue();
            dataSource.close();

        }

    }

    @Override
    public void onEnable() {

        plugin = this;
        PluginManager pm = Bukkit.getServer().getPluginManager();
        loadCanonicalConfig();
        if (canonicalConfig == null) {

            pm.disablePlugin(this);
            return;

        }

        Version bukkitversion = getServerVersion(getServer().getVersion());
        Version minversion = new Version("1.13");
        // check CraftBukkit version
        if (bukkitversion.compareTo(minversion) >= 0) {

            GameModeInventoriesConfig tc = new GameModeInventoriesConfig(this);
            tc.checkConfig();
            loadDatabase();
            if (!hasDatabaseConnection()) {

                getLogger().log(Level.SEVERE, "Database setup failed, disabling plugin.");
                pm.disablePlugin(this);
                return;

            }

            // update database add and populate block fields
            if (!getConfig().getBoolean("blocks_conversion_done")) {

                new GameModeInventoriesBlocksConverter(this).convertBlocksTable();
                plugin.getLogger().log(Level.INFO, "[GameModeInventories] Blocks conversion successful :)");
                plugin.getLogger().log(Level.INFO,
                        "[GameModeInventories] config.yml is immutable; leaving blocks_conversion_done unchanged.");

            }

            // check if creative world exists
            if (getConfig().getBoolean("creative_world.switch_to")) {

                World creative = getServer().getWorld(getConfig().getString("creative_world.world"));
                if (creative == null) {

                    plugin.getLogger().log(Level.INFO,
                            "[GameModeInventories] Creative world specified in the config was not found; world switching will stay disabled until config.yml is updated manually.");

                }

            }

            block = new GameModeInventoriesBlock(this);
            m = new GameModeInventoriesMessage(this);
            m.getMessages();
            m.updateMessages();
            try {

                db_level = GMIDebug.valueOf(getConfig().getString("debug_level"));

            } catch (IllegalArgumentException e) {

                db_level = GMIDebug.ERROR;

            }

            inventoryHandler = new GameModeInventoriesInventory(this);
            // Published before the listeners so anything reacting to enable can read it.
            gameModePolicy = new GameModePolicy(this);
            getServer().getServicesManager().register(GameModePolicy.class, gameModePolicy, this,
                    ServicePriority.Normal);
            pm.registerEvents(new GameModeInventoriesListener(this), this);
            pm.registerEvents(new GameModeInventoriesChunkLoadListener(this), this);
            pm.registerEvents(new GameModeInventoriesDeath(this), this);
            pm.registerEvents(new GameModeInventoriesBlockListener(this), this);
            if (getConfig().getBoolean("track_creative_place.dont_track_is_whitelist")) {

                pm.registerEvents(new GameModeInventoriesTrackWhiteListener(this), this);

            } else {

                pm.registerEvents(new GameModeInventoriesTrackBlackListener(this), this);

            }

            pm.registerEvents(new GameModeInventoriesPistonListener(this), this);
            pm.registerEvents(new GameModeInventoriesCommandListener(this), this);
            pm.registerEvents(new GameModeInventoriesWorldListener(this), this);
            pm.registerEvents(new GameModeInventoriesEntityListener(this), this);
            pm.registerEvents(new GameModeInventoriesPhysicsListener(this), this);
            pm.registerEvents(new GameModeInventoriesVillagerListener(this), this);
            GameModeInventoriesCommands command = new GameModeInventoriesCommands(this);
            getCommand("gmi").setExecutor(command);
            getCommand("gmi").setTabCompleter(command);
            new GameModeInventoriesStand(this).loadStands();
            loadBlackList();
            loadNoTrackList();
            setUpBlockLogger();
            actionRecorderTask();

        } else {

            message(getServer().getConsoleSender(), Component
                    .text("This plugin requires CraftBukkit/Spigot 1.9 or higher, disabling...", NamedTextColor.RED));
            pm.disablePlugin(this);

        }

    }

    private void loadCanonicalConfig() {

        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {

            saveResource("config.yml", false);
            getLogger().log(Level.INFO, "Copied default config.yml to " + configFile.getAbsolutePath());

        }

        canonicalConfig = YamlConfiguration.loadConfiguration(configFile);

    }

    @Override
    public FileConfiguration getConfig() {

        return (canonicalConfig != null) ? canonicalConfig : super.getConfig();

    }

    private Version getServerVersion(String s) {

        Pattern pat = Pattern.compile("\\((.+?)\\)", Pattern.DOTALL);
        Matcher mat = pat.matcher(s);
        String v;
        if (mat.find()) {

            String[] split = mat.group(1).split(" ");
            String[] tmp = split[1].split("-");
            if (tmp.length > 1) {

                v = tmp[0];

            } else {

                v = split[1];

            }

        } else {

            v = "1.7.10";

        }

        return new Version(v);

    }

    private void loadDatabase() {

        String dbtype = getConfig().getString("storage.database");
        prefix = getConfig().getString("storage.prefix");
        try {

            if (dbtype.equals("sqlite")) {

                GameModeInventoriesSQLiteConnectionPool pool = new GameModeInventoriesSQLiteConnectionPool(this);
                dataSource = pool.getHikari();
                GameModeInventoriesSQLite sqlite = new GameModeInventoriesSQLite(this);
                sqlite.createTables();

            } else {

                GameModeInventoriesMySQLConnectionPool pool = new GameModeInventoriesMySQLConnectionPool(this);
                dataSource = pool.getHikari();
                GameModeInventoriesMySQL mysql = new GameModeInventoriesMySQL(this);
                mysql.createTables();

            }

        } catch (ClassNotFoundException e) {

            message(getServer().getConsoleSender(), Component.text("Connection and Tables Error: " + e));

        }

    }

    public Connection getDatabaseConnection() {

        Connection con = null;
        if (dataSource == null) {

            debug("Could not get database connection: datasource is not initialized");
            return null;

        }

        try {

            con = dataSource.getConnection();

        } catch (SQLException e) {

            debug("Could not get database connection: " + e.getMessage());

        }

        return con;

    }

    public boolean hasDatabaseConnection() {

        return dataSource != null && !dataSource.isClosed();

    }

    // Loads block logger support if available.
    public void setUpBlockLogger() {

        blockLogger = new GameModeInventoriesBlockLogger(this);
        blockLogger.enableLogger();

    }

    public GameModeInventoriesBlockLogger getBlockLogger() {

        return blockLogger;

    }

    // The gamemode rules this server enforces; also on the services manager, and
    // plugins that cannot compile against this one reach it via this getter.
    public GameModePolicy getGameModePolicy() {

        return gameModePolicy;

    }

    // Flags plugin driven mode changes so forced Survival stays quiet.
    public void internalGameModeChange(Player p, GameMode mode) {

        internalSwitches.add(p.getUniqueId());
        try {

            p.setGameMode(mode);

        } finally {

            internalSwitches.remove(p.getUniqueId());

        }

    }

    public boolean isInternalGameModeChange(Player p) {

        return internalSwitches.contains(p.getUniqueId());

    }

    // Sends a message to a player or the console with the plugin prefix in front.
    public void message(Audience audience, Component body) {

        // empty root keeps the gold prefix from bleeding into the message body
        audience.sendMessage(Component.empty().append(MY_PLUGIN_NAME).append(body));

    }

    public void debug(Object o, GMIDebug b) {

        if (getConfig().getBoolean("debug") == true) {

            if (b.equals(db_level) || b.equals(GMIDebug.ALL)) {

                message(getServer().getConsoleSender(), Component.text("Debug: " + o));

            }

        }

    }

    public void debug(Object o) {

        debug(o, GMIDebug.ERROR);

    }

    public GameModeInventoriesInventory getInventoryHandler() {

        return inventoryHandler;

    }

    public GameModeInventoriesBlock getBlock() {

        return block;

    }

    public HashMap<String, List<String>> getCreativeBlocks() {

        return creativeBlocks;

    }

    public List<Material> getBlackList() {

        return blackList;

    }

    private void loadBlackList() {

        List<String> bl = getConfig().getStringList("blacklist");
        bl.forEach((s) -> {

            try {

                blackList.add(Material.valueOf(s));

            } catch (IllegalArgumentException iae) {

                message(getServer().getConsoleSender(), m.get("INVALID_MATERIAL").append(Component.text(" " + s)));

            }

        });

    }

    public List<Material> getNoTrackList() {

        return noTrackList;

    }

    private void loadNoTrackList() {

        List<String> ntl = getConfig().getStringList("track_creative_place.dont_track");
        ntl.forEach((s) -> {

            try {

                noTrackList.add(Material.valueOf(s));

            } catch (IllegalArgumentException iae) {

                message(getServer().getConsoleSender(),
                        m.get("INVALID_MATERIAL_TRACK").append(Component.text(" " + s)));

            }

        });

    }

    public List<String> getPoints() {

        return points;

    }

    public List<UUID> getStands() {

        return stands;

    }

    public GameModeInventoriesMessage getM() {

        return m;

    }

    public String getPrefix() {

        return prefix;

    }

    public void actionRecorderTask() {

        int recorder_tick_delay = 3;
        // we schedule it once, it will reschedule itself
        recordingTask = getServer().getScheduler().runTaskLaterAsynchronously(this,
                new GameModeInventoriesRecordingTask(this), recorder_tick_delay);

    }

}
