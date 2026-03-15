package com.cubetale.events.config;

import com.cubetale.events.CubeTaleEvents;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public class EventsConfig {

    private final CubeTaleEvents plugin;
    private FileConfiguration cfg;

    public EventsConfig(CubeTaleEvents plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        cfg = plugin.getConfig();
    }

    public FileConfiguration get() { return cfg; }

    // General
    public String getPrefix() { return color(cfg.getString("general.prefix", "&6[Events] &r")); }
    public boolean isEventJoinBroadcast() { return cfg.getBoolean("general.event-join-broadcast", true); }
    public boolean isEventLeaveBroadcast() { return cfg.getBoolean("general.event-leave-broadcast", true); }
    public int getMinPlayersToStart() { return cfg.getInt("general.min-players-to-start", 2); }
    public String getSound(String key) { return cfg.getString("general.sounds." + key, ""); }

    // Discord
    public boolean isDiscordEnabled() { return cfg.getBoolean("discord.enabled", false); }
    public String getDiscordChannelId() { return cfg.getString("discord.channel-id", ""); }
    public String getDiscordWebhookUrl() { return cfg.getString("discord.webhook-url", ""); }
    public String getDiscordColor(String key) { return cfg.getString("discord.colors." + key, "#FFD700"); }
    public boolean isDiscordAnnounceStart() { return cfg.getBoolean("discord.announce-start", true); }
    public boolean isDiscordAnnounceEnd() { return cfg.getBoolean("discord.announce-end", true); }
    public boolean isDiscordAnnounceWinner() { return cfg.getBoolean("discord.announce-winner", true); }

    // Scheduler
    public boolean isSchedulerEnabled() { return cfg.getBoolean("scheduler.enabled", true); }
    public int getSchedulerIntervalMinutes() { return cfg.getInt("scheduler.interval-minutes", 60); }
    public int getPreAnnounceMinutes() { return cfg.getInt("scheduler.pre-announce-minutes", 5); }
    public boolean isSchedulerUseVote() { return cfg.getBoolean("scheduler.use-vote", true); }
    public int getVoteDurationSeconds() { return cfg.getInt("scheduler.vote-duration", 60); }
    public List<String> getSchedulerRotation() { return cfg.getStringList("scheduler.rotation"); }
    public List<String> getBlackoutHours() { return cfg.getStringList("scheduler.blackout-hours"); }
    public int getSchedulerMinOnlinePlayers() { return cfg.getInt("scheduler.min-online-players", 2); }

    // Leaderboard
    public boolean isLeaderboardEnabled() { return cfg.getBoolean("leaderboard.enabled", true); }
    public String getLeaderboardDatabase() { return cfg.getString("leaderboard.database", "SQLITE"); }
    public String getSqliteFile() { return cfg.getString("leaderboard.sqlite.file", "leaderboard.db"); }
    public String getMysqlHost() { return cfg.getString("leaderboard.mysql.host", "localhost"); }
    public int getMysqlPort() { return cfg.getInt("leaderboard.mysql.port", 3306); }
    public String getMysqlDatabase() { return cfg.getString("leaderboard.mysql.database", "cubetale_events"); }
    public String getMysqlUser() { return cfg.getString("leaderboard.mysql.username", "root"); }
    public String getMysqlPassword() { return cfg.getString("leaderboard.mysql.password", ""); }
    public int getMysqlPoolSize() { return cfg.getInt("leaderboard.mysql.pool-size", 5); }
    public int getLeaderboardTopCount() { return cfg.getInt("leaderboard.top-count", 10); }

    // Rewards
    public List<String> getFirstPlaceCommands() { return cfg.getStringList("rewards.first-place.commands"); }
    public List<String> getSecondPlaceCommands() { return cfg.getStringList("rewards.second-place.commands"); }
    public List<String> getThirdPlaceCommands() { return cfg.getStringList("rewards.third-place.commands"); }
    public List<String> getParticipationCommands() { return cfg.getStringList("rewards.participation.commands"); }
    public boolean isParticipationEnabled() { return cfg.getBoolean("rewards.participation.enabled", true); }
    public boolean isParticipationRequireScore() { return cfg.getBoolean("rewards.participation.require-score", true); }
    public String getFirstPlaceBroadcast() { return color(cfg.getString("rewards.first-place.broadcast", "")); }

    // Debug
    public boolean isDebug() { return cfg.getBoolean("debug", false); }

    private String color(String s) {
        if (s == null) return "";
        return s.replace("&", "\u00a7");
    }
}
