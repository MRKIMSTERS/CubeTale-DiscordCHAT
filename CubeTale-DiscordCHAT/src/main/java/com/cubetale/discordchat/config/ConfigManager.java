package com.cubetale.discordchat.config;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import org.bukkit.configuration.file.FileConfiguration;

public class ConfigManager {

    private final CubeTaleDiscordChat plugin;
    private FileConfiguration config;

    public ConfigManager(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void reload() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
    }

    // Discord
    public String getBotToken() {
        return config.getString("discord.token", "");
    }

    public String getGuildId() {
        return config.getString("discord.guild-id", "");
    }

    public String getChatChannelId() {
        return config.getString("discord.channels.chat", "");
    }

    public String getConsoleChannelId() {
        return config.getString("discord.channels.console", "");
    }

    public String getLogChannelId() {
        return config.getString("discord.channels.log", "");
    }

    public String getAdminRoleId() {
        return config.getString("discord.roles.admin", "");
    }

    public String getModRoleId() {
        return config.getString("discord.roles.moderator", "");
    }

    // Webhook
    public boolean isWebhookEnabled() {
        return config.getBoolean("discord.webhook.enabled", true);
    }

    public String getWebhookUrl() {
        return config.getString("discord.webhook.url", "");
    }

    public String getAvatarService() {
        return config.getString("discord.webhook.avatar-service", "crafatar");
    }

    public int getAvatarSize() {
        return config.getInt("discord.webhook.avatar-size", 128);
    }

    public boolean isAvatarOverlay() {
        return config.getBoolean("discord.webhook.show-overlay", true);
    }

    // Chat sync
    public boolean isChatSyncEnabled() {
        return config.getBoolean("chat-sync.enabled", true);
    }

    public boolean isMinecraftToDiscordEnabled() {
        return config.getBoolean("chat-sync.minecraft-to-discord", true);
    }

    public boolean isDiscordToMinecraftEnabled() {
        return config.getBoolean("chat-sync.discord-to-minecraft", true);
    }

    public String getDiscordToMinecraftFormat() {
        return config.getString("chat-sync.formats.discord-to-minecraft",
                "&8[&9Discord&8] &7{display-name}&8: &f{message}");
    }

    public int getMaxMessageLength() {
        return config.getInt("chat-sync.filter.max-length", 2000);
    }

    public boolean isLinksAllowed() {
        return config.getBoolean("chat-sync.filter.allow-links", false);
    }

    public boolean isStripColors() {
        return config.getBoolean("chat-sync.filter.strip-colors", true);
    }

    // Linking
    public boolean isLinkingEnabled() {
        return config.getBoolean("linking.enabled", true);
    }

    public boolean isLinkRequired() {
        return config.getBoolean("linking.require-link", false);
    }

    public String getVerificationMethod() {
        return config.getString("linking.verification-method", "CODE");
    }

    public int getCodeLength() {
        return config.getInt("linking.code-length", 6);
    }

    public int getCodeExpiry() {
        return config.getInt("linking.code-expiry", 300);
    }

    // Database
    public String getDatabaseType() {
        return config.getString("database.type", "SQLITE").toUpperCase();
    }

    public String getMysqlHost() {
        return config.getString("database.mysql.host", "localhost");
    }

    public int getMysqlPort() {
        return config.getInt("database.mysql.port", 3306);
    }

    public String getMysqlDatabase() {
        return config.getString("database.mysql.database", "cubetale_discord");
    }

    public String getMysqlUsername() {
        return config.getString("database.mysql.username", "root");
    }

    public String getMysqlPassword() {
        return config.getString("database.mysql.password", "");
    }

    public int getMysqlPoolSize() {
        return config.getInt("database.mysql.pool-size", 10);
    }

    public String getSqliteFile() {
        return config.getString("database.sqlite.file", "database.db");
    }

    // Events
    public boolean isJoinEnabled() {
        return config.getBoolean("events.join.enabled", true);
    }

    public boolean isLeaveEnabled() {
        return config.getBoolean("events.leave.enabled", true);
    }

    public boolean isDeathEnabled() {
        return config.getBoolean("events.death.enabled", true);
    }

    public boolean isAdvancementEnabled() {
        return config.getBoolean("events.advancement.enabled", true);
    }

    public boolean isServerStartEnabled() {
        return config.getBoolean("events.server-start.enabled", true);
    }

    public boolean isServerStopEnabled() {
        return config.getBoolean("events.server-stop.enabled", true);
    }

    public boolean isShowDeathCoordinates() {
        return config.getBoolean("events.death.show-coordinates", true);
    }

    // Console
    public boolean isConsoleEnabled() {
        return config.getBoolean("console.enabled", true);
    }

    public boolean isFilterSensitive() {
        return config.getBoolean("console.filter-sensitive", true);
    }

    // Activity
    public boolean isActivityEnabled() {
        return config.getBoolean("discord.activity.enabled", true);
    }

    public String getActivityType() {
        return config.getString("discord.activity.type", "PLAYING");
    }

    public String getActivityText() {
        return config.getString("discord.activity.text", "{online}/{max} players");
    }

    public int getActivityUpdateInterval() {
        return config.getInt("discord.activity.update-interval", 60);
    }

    // PlaceholderAPI
    public String getUsernameFormat() {
        return config.getString("placeholderapi.username-format", "");
    }

    // Debug
    public boolean isDebugEnabled() {
        return config.getBoolean("debug", false);
    }
}
