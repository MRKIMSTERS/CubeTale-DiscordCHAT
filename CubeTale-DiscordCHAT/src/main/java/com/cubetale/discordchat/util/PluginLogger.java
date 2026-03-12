package com.cubetale.discordchat.util;

import com.cubetale.discordchat.CubeTaleDiscordChat;

import java.util.logging.Level;

public class PluginLogger {

    private final CubeTaleDiscordChat plugin;
    private static final String PREFIX = "[CubeTale-DiscordCHAT] ";

    public PluginLogger(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    public void info(String message) {
        plugin.getLogger().info(message);
    }

    public void warning(String message) {
        plugin.getLogger().warning(message);
    }

    public void severe(String message) {
        plugin.getLogger().severe(message);
    }

    public void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + message);
        }
    }

    public void debug(String message, Throwable throwable) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().log(Level.INFO, "[DEBUG] " + message, throwable);
        }
    }

    public void log(Level level, String message, Throwable throwable) {
        plugin.getLogger().log(level, message, throwable);
    }
}
