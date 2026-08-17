/*
 *  Copyright 2014 eccentric_nz.
 */
package me.eccentric_nz.gamemodeinventories;

import java.io.*;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class GameModeInventoriesMessage {

    // Matches the colour codes of messages.yml files predating the MiniMessage
    // switch.
    private static final Pattern LEGACY_CODES = Pattern.compile("[&§][0-9a-fk-orA-FK-OR]");

    private final GameModeInventories plugin;
    private final HashMap<String, String> message = new HashMap<>();
    private final FileConfiguration messagesConfig;
    private final File messagesFile;
    HashMap<String, String> messageOptions = new HashMap<>();

    public GameModeInventoriesMessage(GameModeInventories plugin) {

        this.plugin = plugin;
        messagesFile = getMessagesFile();
        messagesConfig = YamlConfiguration.loadConfiguration(messagesFile);
        messageOptions.put("CONFIG_SET", "<green><option> was set to: <value>");
        messageOptions.put("FORCED_SURVIVAL",
                "<red>Notice: <gold>You were switched to Survival mode because you do not have permission for Creative mode here.");
        messageOptions.put("HELP",
                "<gold>Simply switch game modes, and your inventory, armor and xp will change with you.");
        messageOptions.put("INVALID_MATERIAL", "<red>Error: <gold>Invalid material in blacklist");
        messageOptions.put("INVALID_MATERIAL_TRACK", "<red>Error: <gold>Invalid material in dont_track list");
        messageOptions.put("NO_CREATIVE_BREAK",
                "<red>Error: <gold>You cannot break blocks that were placed in Creative mode!");
        messageOptions.put("NO_CREATIVE_COMMAND",
                "<red>Error: <gold>You are not allowed to use <green><command></green> in Creative mode!");
        messageOptions.put("NO_CREATIVE_DROPS",
                "<red>Error: <gold>Blocks that were placed in Creative mode do not give drops!");
        messageOptions.put("NO_CREATIVE_HORSE",
                "<red>Error: <gold>You are not allowed to access horse inventories in Creative mode!");
        messageOptions.put("NO_CREATIVE_INVENTORY",
                "<red>Error: <gold>You are not allowed to access inventories in Creative mode!");
        messageOptions.put("NO_CREATIVE_PICKUP",
                "<red>Error: <gold>You are not allowed to pick up items in Creative mode!");
        messageOptions.put("NO_CREATIVE_PLACE", "<red>Error: <gold><material> placement is disabled in Creative mode!");
        messageOptions.put("NO_CREATIVE_REGION",
                "<red>Error: <gold>You do not have permission to enter Creative mode in this region!");
        messageOptions.put("NO_PERMISSION", "<red>Error: <gold>You do not have permission to run that command!");
        messageOptions.put("NO_PLAYER_DROPS", "<red>Error: <gold>You are not allowed to drop items in Creative mode!");
        messageOptions.put("NO_WORKBENCH_DROPS", "<red>Error: <gold>Workbenches do not drop items in Creative mode!");
        messageOptions.put("NO_SPECTATOR", "<red>Error: <gold>You are not allowed to be a Spectator!");
        messageOptions.put("NO_ADVENTURE", "<red>Error: <gold>Adventure mode is not allowed in this world!");
        messageOptions.put("NO_TRADE",
                "<red>Error: <gold>You are not allowed to trade with villagers in Creative mode!");

    }

    public void getMessages() {

        messagesConfig.getKeys(false).forEach((m) -> {

            String configuredMessage = messagesConfig.getString(m);
            if (configuredMessage != null) {

                // stored raw - get() picks MiniMessage or the legacy reader per message
                message.put(m, configuredMessage);

            }

        });

    }

    // Gets a message as a component - args fill any placeholders the message
    // declares.
    public Component get(String key, Arg... args) {

        String raw = message.get(key);
        if (raw == null) {

            raw = messageOptions.getOrDefault(key, key);

        }

        if (LEGACY_CODES.matcher(raw).find()) {

            // messages.yml predates the MiniMessage switch, so read it the old way
            return legacy(raw, args);

        }

        final TagResolver.Builder resolvers = TagResolver.builder();
        for (Arg arg : args) {

            // unparsed, so player supplied text cannot smuggle in tags of its own
            resolvers.resolver(Placeholder.unparsed(arg.name(), arg.value()));

        }

        return MiniMessage.miniMessage().deserialize(raw, resolvers.build());

    }

    private Component legacy(String raw, Arg... args) {

        String formatted = raw;
        if (args.length > 0) {

            final Object[] values = new Object[args.length];
            for (int i = 0; i < args.length; i++) {

                values[i] = args[i].value();

            }

            try {

                formatted = String.format(raw, values);

            } catch (IllegalFormatException e) {

                plugin.debug("Could not format legacy message '" + raw + "', " + e.getMessage());

            }

        }

        return LegacyComponentSerializer.legacyAmpersand().deserialize(formatted);

    }

    private File getMessagesFile() {

        File file = new File(plugin.getDataFolder(), "messages.yml");
        InputStream in = plugin.getResource("messages.yml");
        if (!file.exists()) {

            OutputStream out;
            try {

                out = new FileOutputStream(file);
                byte[] buf = new byte[1024];
                int len;
                try {

                    while ((len = in.read(buf)) > 0) {

                        out.write(buf, 0, len);

                    }

                } catch (IOException io) {

                    plugin.getLogger().log(Level.WARNING,
                            "[GameModeInventories] Could not save the file (" + file.toString() + ").");

                } finally {

                    try {

                        out.close();

                    } catch (IOException e) {

                    }

                }

            } catch (FileNotFoundException e) {

                plugin.getLogger().log(Level.WARNING, "[GameModeInventories] File not found.");

            } finally {

                if (in != null) {

                    try {

                        in.close();

                    } catch (IOException e) {

                    }

                }

            }

        }

        return file;

    }

    public void updateMessages() {

        int m = 0;
        for (Map.Entry<String, String> entry : messageOptions.entrySet()) {

            if (!messagesConfig.contains(entry.getKey())) {

                messagesConfig.set(entry.getKey(), entry.getValue());
                message.put(entry.getKey(), entry.getValue());
                m++;

            }

        }

        if (m > 0) {

            try {

                messagesConfig.save(messagesFile);
                plugin.getLogger().log(Level.INFO, "Added " + m + " new items to messages.yml");

            } catch (IOException ex) {

                plugin.debug("Could not save messages.yml, " + ex.getMessage());

            }

        }

    }

    // A message placeholder - name matches a MiniMessage tag, falls back to legacy
    // %s.
    public record Arg(String name, String value) {

    }

}
