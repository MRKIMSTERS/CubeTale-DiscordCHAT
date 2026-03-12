package com.cubetale.discordchat.config;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class MessagesConfig {

    private final CubeTaleDiscordChat plugin;
    private FileConfiguration config;
    private File configFile;

    public MessagesConfig(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        load();
    }

    private void load() {
        configFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!configFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(configFile);

        InputStream defaultStream = plugin.getResource("messages.yml");
        if (defaultStream != null) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream));
            config.setDefaults(defaults);
        }
    }

    public void reload() {
        load();
    }

    public String get(String path) {
        String msg = config.getString(path, "&cMissing message: " + path);
        String prefix = config.getString("prefix", "&8[&9CubeTale&8-&bDiscord&8] ");
        msg = msg.replace("{prefix}", prefix);
        return ChatColor.translateAlternateColorCodes('&', msg);
    }

    public String get(String path, Map<String, String> placeholders) {
        String msg = get(path);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return msg;
    }

    public String get(String path, String... keyValues) {
        Map<String, String> map = new HashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return get(path, map);
    }
}
