package com.cubetale.discordchat.discord;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.MessageFormatter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Posts (and continuously edits) a live server-stats embed in a dedicated
 * Discord channel, refreshing every N seconds.
 *
 * Configured via:
 *   stats.enabled       — toggle
 *   stats.channel-id    — Discord channel ID
 *   stats.update-interval — seconds between refreshes (default 300)
 */
public class StatsAutoPost {

    private final CubeTaleDiscordChat plugin;

    /** ID of the last posted stats message, -1 if none yet. */
    private final AtomicLong lastMessageId = new AtomicLong(-1L);

    private BukkitTask task;

    public StatsAutoPost(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!plugin.getConfigManager().isStatsAutoPostEnabled()) return;
        String channelId = plugin.getConfigManager().getStatsChannelId();
        if (channelId == null || channelId.isEmpty() || channelId.equals("STATS_CHANNEL_ID")) {
            plugin.getPluginLogger().warning(
                    "Stats auto-post is enabled but stats.channel-id is not configured.");
            return;
        }

        int interval = Math.max(30, plugin.getConfigManager().getStatsUpdateInterval());
        // Initial post 10 seconds after start; repeats every interval seconds
        task = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::collectAndPost,
                20L * 10,
                20L * interval
        );
        plugin.getPluginLogger().info(
                "Server stats auto-post started (interval: " + interval + "s, channel: " + channelId + ").");
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    // ── Core ────────────────────────────────────────────────────────────────────

    private void collectAndPost() {
        // Snapshot data that is safe to read from async context
        int online   = Bukkit.getOnlinePlayers().size();
        int max      = Bukkit.getMaxPlayers();
        String version = Bukkit.getVersion().split("\\(")[0].trim();

        long uptimeMs = System.currentTimeMillis() - plugin.getDiscordBot().getStartTime();
        String uptime = MessageFormatter.formatUptime(uptimeMs / 1000);

        double tps = getServerTps();
        long freeRam = Runtime.getRuntime().freeMemory()  / (1024 * 1024);
        long usedRam = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long maxRam  = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        // Build player list string
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        String playerList = players.isEmpty()
                ? "*No players online*"
                : players.stream()
                         .map(p -> "▸ " + p.getName())
                         .collect(Collectors.joining("\n"));
        if (playerList.length() > 1024) playerList = playerList.substring(0, 1020) + "...";

        // Status indicators
        String tpsIcon   = tps >= 19.0 ? "🟢" : (tps >= 15.0 ? "🟡" : "🔴");
        int    embedColor = tps >= 19.0 ? 0x55FF55 : (tps >= 15.0 ? 0xFFFF55 : 0xFF5555);

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📡 Server Status")
                .setColor(embedColor)
                .addField("🟢 Status",     "Online",                          true)
                .addField("👥 Players",    online + " / " + max,              true)
                .addField("⏱️ Uptime",     uptime,                            true)
                .addField(tpsIcon + " TPS",String.format("%.1f / 20.0", tps), true)
                .addField("💾 RAM",        usedRam + " MB / " + maxRam + " MB", true)
                .addField("🖥️ Version",   version,                           true)
                .addField("👤 Online Players", playerList,                   false)
                .setFooter("Last updated")
                .setTimestamp(Instant.now());

        postOrEdit(embed);
    }

    private void postOrEdit(EmbedBuilder embed) {
        String channelId = plugin.getConfigManager().getStatsChannelId();
        if (channelId == null || channelId.isEmpty()) return;
        if (!plugin.getDiscordBot().isConnected()) return;

        TextChannel channel = plugin.getDiscordBot().getJda().getTextChannelById(channelId);
        if (channel == null) {
            plugin.getPluginLogger().warning("Stats channel not found: " + channelId);
            return;
        }

        long msgId = lastMessageId.get();
        if (msgId != -1L) {
            // Try to edit the existing message
            channel.editMessageEmbedsById(msgId, embed.build()).queue(
                    msg -> { /* edited OK */ },
                    err -> {
                        // Message was deleted or inaccessible — post a new one
                        lastMessageId.set(-1L);
                        postNew(channel, embed);
                    }
            );
        } else {
            postNew(channel, embed);
        }
    }

    private void postNew(TextChannel channel, EmbedBuilder embed) {
        channel.sendMessageEmbeds(embed.build()).queue(
                msg -> lastMessageId.set(msg.getIdLong()),
                err -> plugin.getPluginLogger().warning("Failed to post stats embed: " + err.getMessage())
        );
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private double getServerTps() {
        try {
            java.lang.reflect.Method getTps = Bukkit.getServer().getClass().getMethod("getTPS");
            double[] arr = (double[]) getTps.invoke(Bukkit.getServer());
            if (arr != null && arr.length > 0) return Math.min(arr[0], 20.0);
        } catch (Exception ignored) {}
        return 20.0;
    }
}
