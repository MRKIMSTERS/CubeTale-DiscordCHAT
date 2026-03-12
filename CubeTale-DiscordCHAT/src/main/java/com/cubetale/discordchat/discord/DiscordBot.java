package com.cubetale.discordchat.discord;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.ColorConverter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bukkit.scheduler.BukkitTask;

import java.awt.*;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class DiscordBot {

    private final CubeTaleDiscordChat plugin;
    private JDA jda;
    private boolean connected = false;
    private BukkitTask activityTask;
    private long startTime;

    public DiscordBot(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        this.startTime = System.currentTimeMillis();
    }

    public boolean start() {
        String token = plugin.getConfigManager().getBotToken();
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getPluginLogger().warning("Discord bot token is not configured in config.yml!");
            return false;
        }

        try {
            plugin.getPluginLogger().info("Connecting to Discord...");

            JDABuilder builder = JDABuilder.createDefault(token)
                    .enableIntents(
                            GatewayIntent.GUILD_MESSAGES,
                            GatewayIntent.GUILD_MEMBERS,
                            GatewayIntent.MESSAGE_CONTENT,
                            GatewayIntent.DIRECT_MESSAGES
                    )
                    .setMemberCachePolicy(MemberCachePolicy.ALL)
                    .setChunkingFilter(ChunkingFilter.ALL)
                    .setStatus(OnlineStatus.ONLINE)
                    .addEventListeners(new DiscordListener(plugin));

            jda = builder.build();
            jda.awaitReady();

            connected = true;
            plugin.getPluginLogger().info("Successfully connected to Discord! Bot: " + jda.getSelfUser().getAsTag());

            // Register slash commands
            SlashCommandManager slashCommandManager = new SlashCommandManager(plugin, jda);
            slashCommandManager.registerCommands();
            jda.addEventListener(slashCommandManager);

            // Start activity update task
            startActivityTask();

            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getPluginLogger().severe("Interrupted while connecting to Discord.");
            return false;
        } catch (Exception e) {
            plugin.getPluginLogger().severe("Failed to connect to Discord: " + e.getMessage());
            plugin.getPluginLogger().debug("Discord connection error", e);
            return false;
        }
    }

    private void startActivityTask() {
        if (!plugin.getConfigManager().isActivityEnabled()) return;

        int interval = plugin.getConfigManager().getActivityUpdateInterval();
        activityTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            updateActivity();
        }, 0L, interval * 20L);
    }

    public void updateActivity() {
        if (jda == null || !connected) return;

        String text = plugin.getConfigManager().getActivityText()
                .replace("{online}", String.valueOf(plugin.getServer().getOnlinePlayers().size()))
                .replace("{max}", String.valueOf(plugin.getServer().getMaxPlayers()));

        Activity activity;
        switch (plugin.getConfigManager().getActivityType().toUpperCase()) {
            case "WATCHING":
                activity = Activity.watching(text);
                break;
            case "LISTENING":
                activity = Activity.listening(text);
                break;
            case "COMPETING":
                activity = Activity.competing(text);
                break;
            case "PLAYING":
            default:
                activity = Activity.playing(text);
                break;
        }
        jda.getPresence().setActivity(activity);
    }

    public void shutdown() {
        if (activityTask != null) {
            activityTask.cancel();
        }
        if (jda != null) {
            jda.shutdown();
            try {
                if (!jda.awaitShutdown(10, TimeUnit.SECONDS)) {
                    jda.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                jda.shutdownNow();
            }
            connected = false;
            plugin.getPluginLogger().info("Discord bot disconnected.");
        }
    }

    // --- Message sending ---

    public void sendMessage(String channelId, String message) {
        if (!isConnected() || channelId == null || channelId.isEmpty()) return;
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            plugin.getPluginLogger().debug("Channel not found: " + channelId);
            return;
        }
        channel.sendMessage(message).queue(
                success -> plugin.getPluginLogger().debug("Message sent to #" + channel.getName()),
                failure -> plugin.getPluginLogger().warning("Failed to send message: " + failure.getMessage())
        );
    }

    public void sendEmbed(String channelId, MessageEmbed embed) {
        if (!isConnected() || channelId == null || channelId.isEmpty()) return;
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            plugin.getPluginLogger().debug("Channel not found: " + channelId);
            return;
        }
        channel.sendMessageEmbeds(embed).queue(
                success -> plugin.getPluginLogger().debug("Embed sent to #" + channel.getName()),
                failure -> plugin.getPluginLogger().warning("Failed to send embed: " + failure.getMessage())
        );
    }

    // --- Event notifications ---

    public void sendServerStartNotification() {
        if (!plugin.getConfigManager().isServerStartEnabled()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🟢 Server Started")
                .setDescription("The Minecraft server is now online!")
                .setColor(ColorConverter.hexToInt("#00FFFF"))
                .setTimestamp(Instant.now())
                .setFooter("Version: " + plugin.getServer().getVersion());

        sendEmbed(chatChannelId, embed.build());
    }

    public void sendServerStopNotification() {
        if (!plugin.getConfigManager().isServerStopEnabled()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("🔴 Server Stopping")
                .setDescription("The Minecraft server is shutting down.")
                .setColor(ColorConverter.hexToInt("#FF8800"))
                .setTimestamp(Instant.now());

        sendEmbed(chatChannelId, embed.build());
    }

    public void sendPlayerJoinNotification(String playerName, String playerUUID, String avatarUrl) {
        if (!plugin.getConfigManager().isJoinEnabled()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("✅ Player Joined")
                .setDescription("**" + playerName + "** joined the server!")
                .setColor(ColorConverter.hexToInt("#00FF00"))
                .setThumbnail(avatarUrl)
                .setTimestamp(Instant.now())
                .setFooter("Players: " + plugin.getServer().getOnlinePlayers().size()
                        + "/" + plugin.getServer().getMaxPlayers());

        sendEmbed(chatChannelId, embed.build());
    }

    public void sendPlayerLeaveNotification(String playerName, String playerUUID, String avatarUrl) {
        if (!plugin.getConfigManager().isLeaveEnabled()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("❌ Player Left")
                .setDescription("**" + playerName + "** left the server.")
                .setColor(ColorConverter.hexToInt("#FF5555"))
                .setThumbnail(avatarUrl)
                .setTimestamp(Instant.now())
                .setFooter("Players: " + plugin.getServer().getOnlinePlayers().size()
                        + "/" + plugin.getServer().getMaxPlayers());

        sendEmbed(chatChannelId, embed.build());
    }

    public void sendPlayerDeathNotification(String playerName, String playerUUID,
                                            String deathMessage, String avatarUrl) {
        if (!plugin.getConfigManager().isDeathEnabled()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("💀 Player Death")
                .setDescription(deathMessage)
                .setColor(ColorConverter.hexToInt("#8B0000"))
                .setThumbnail(avatarUrl)
                .setTimestamp(Instant.now());

        sendEmbed(chatChannelId, embed.build());
    }

    /**
     * Fallback advancement embed (used when webhook is unavailable).
     * The advancement item icon is shown as the thumbnail on the right.
     *
     * @param avatarUrl  Not used in this fallback (only one thumbnail slot).
     * @param iconUrl    Advancement item icon shown as thumbnail. May be null.
     */
    public void sendAdvancementNotification(String playerName, String playerUUID,
                                            String advancement, String description,
                                            String avatarUrl, String iconUrl) {
        if (!plugin.getConfigManager().isAdvancementEnabled()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        String body = "**" + playerName + "** has made the advancement **" + advancement + "**";
        if (description != null && !description.isEmpty()) {
            body += "\n*" + description + "*";
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setDescription(body)
                .setColor(ColorConverter.hexToInt("#55FF55"))
                .setTimestamp(Instant.now());

        // Icon goes in the thumbnail slot (top-right), matching the screenshot layout
        if (iconUrl != null && !iconUrl.isEmpty()) {
            embed.setThumbnail(iconUrl);
        }

        sendEmbed(chatChannelId, embed.build());
    }

    // --- Getters ---

    public boolean isConnected() {
        return connected && jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public JDA getJda() {
        return jda;
    }

    public Guild getGuild() {
        if (jda == null) return null;
        String guildId = plugin.getConfigManager().getGuildId();
        if (guildId == null || guildId.isEmpty()) return null;
        return jda.getGuildById(guildId);
    }

    public long getStartTime() {
        return startTime;
    }
}
