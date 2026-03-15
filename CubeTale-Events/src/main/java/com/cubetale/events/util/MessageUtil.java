package com.cubetale.events.util;

import com.cubetale.events.CubeTaleEvents;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MessageUtil {

    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    private static MessageUtil instance;

    private final CubeTaleEvents plugin;
    private FileConfiguration messages;

    private MessageUtil(CubeTaleEvents plugin) {
        this.plugin = plugin;
        reload();
    }

    public static MessageUtil getInstance(CubeTaleEvents plugin) {
        if (instance == null) instance = new MessageUtil(plugin);
        return instance;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(file);
    }

    public String get(String path) {
        String raw = messages.getString("messages." + path, "&c[Missing message: " + path + "]");
        return colorize(raw);
    }

    public String get(String path, Map<String, String> placeholders) {
        String msg = get(path);
        for (Map.Entry<String, String> e : placeholders.entrySet()) {
            msg = msg.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
        }
        return msg;
    }

    public List<String> getList(String path) {
        List<String> raw = messages.getStringList("messages." + path);
        raw.replaceAll(MessageUtil::colorize);
        return raw;
    }

    public List<String> getList(String path, Map<String, String> placeholders) {
        List<String> list = getList(path);
        list.replaceAll(line -> {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                line = line.replace("{" + e.getKey() + "}", e.getValue() == null ? "" : e.getValue());
            }
            return line;
        });
        return list;
    }

    public void send(CommandSender sender, String path) {
        sender.sendMessage(prefix() + get(path));
    }

    public void send(CommandSender sender, String path, Map<String, String> ph) {
        sender.sendMessage(prefix() + get(path, ph));
    }

    public void broadcast(String path) {
        String msg = prefix() + get(path);
        Bukkit.broadcastMessage(msg);
    }

    public void broadcast(String path, Map<String, String> ph) {
        String msg = prefix() + get(path, ph);
        Bukkit.broadcastMessage(msg);
    }

    public void broadcastList(String path, Map<String, String> ph) {
        for (String line : getList(path, ph)) {
            Bukkit.broadcastMessage(line);
        }
    }

    public void sendTitle(Player player, String titlePath, String subtitlePath, Map<String, String> ph) {
        String title    = get(titlePath, ph);
        String subtitle = get(subtitlePath, ph);
        player.sendTitle(title, subtitle, 10, 40, 10);
    }

    public void broadcastTitle(String titlePath, String subtitlePath, Map<String, String> ph) {
        String title    = get(titlePath, ph);
        String subtitle = get(subtitlePath, ph);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendTitle(title, subtitle, 10, 40, 10);
        }
    }

    public void broadcastActionBar(String text) {
        String colored = colorize(text);
        net.md_5.bungee.api.chat.TextComponent component =
                new net.md_5.bungee.api.chat.TextComponent(colored);
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR, component);
        }
    }

    public String prefix() {
        return plugin.getEventsConfig().getPrefix();
    }

    public static String colorize(String text) {
        if (text == null) return "";
        Matcher matcher = HEX_PATTERN.matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());
        }
        matcher.appendTail(buffer);
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }

    public static String strip(String text) {
        return ChatColor.stripColor(colorize(text));
    }
}
