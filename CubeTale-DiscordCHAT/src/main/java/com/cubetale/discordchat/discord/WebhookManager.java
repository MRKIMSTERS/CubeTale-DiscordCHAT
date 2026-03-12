package com.cubetale.discordchat.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.AvatarUrlBuilder;
import com.cubetale.discordchat.util.MessageFormatter;
import org.bukkit.entity.Player;

public class WebhookManager {

    private final CubeTaleDiscordChat plugin;
    private WebhookClient webhookClient;
    private AvatarUrlBuilder avatarBuilder;

    public WebhookManager(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        // Build avatar URL builder
        String service = plugin.getConfigManager().getAvatarService();
        int size = plugin.getConfigManager().getAvatarSize();
        boolean overlay = plugin.getConfigManager().isAvatarOverlay();
        this.avatarBuilder = new AvatarUrlBuilder(service, size, overlay);

        if (!plugin.getConfigManager().isWebhookEnabled()) {
            plugin.getPluginLogger().info("Webhook is disabled in config.");
            return;
        }

        String webhookUrl = plugin.getConfigManager().getWebhookUrl();
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("YOUR_WEBHOOK_URL_HERE")) {
            plugin.getPluginLogger().warning("Webhook URL is not configured! Player avatars in chat will not work.");
            plugin.getPluginLogger().warning("Set discord.webhook.url in config.yml to enable this feature.");
            return;
        }

        try {
            WebhookClientBuilder builder = new WebhookClientBuilder(webhookUrl);
            builder.setThreadFactory(job -> {
                Thread thread = new Thread(job);
                thread.setName("CubeTale-Webhook");
                thread.setDaemon(true);
                return thread;
            });
            builder.setWait(false);
            this.webhookClient = builder.build();
            plugin.getPluginLogger().info("Webhook client initialized successfully.");
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to initialize webhook client: " + e.getMessage());
        }
    }

    /**
     * Send a player chat message via webhook with their Minecraft skin as avatar.
     */
    public void sendPlayerChat(Player player, String message) {
        if (webhookClient == null) {
            // Fallback to regular bot message if webhook not configured
            sendFallbackBotMessage(player.getName(), message);
            return;
        }

        int size = plugin.getConfigManager().getAvatarSize();
        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, size);
        String username = buildWebhookUsername(player);
        String discordMessage = formatForDiscord(message);

        if (discordMessage.isEmpty()) return;

        try {
            WebhookMessageBuilder builder = new WebhookMessageBuilder();
            builder.setUsername(username);
            builder.setAvatarUrl(avatarUrl);
            builder.setContent(discordMessage);
            webhookClient.send(builder.build());

            plugin.getPluginLogger().debug("Sent webhook message for " + player.getName() + ": " + discordMessage);
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to send webhook message: " + e.getMessage());
            sendFallbackBotMessage(player.getName(), message);
        }
    }

    /**
     * Fallback: send as a regular bot message if webhook is unavailable.
     */
    private void sendFallbackBotMessage(String playerName, String message) {
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;
        if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) return;

        String formattedMessage = "**" + playerName + "**: " + formatForDiscord(message);
        plugin.getDiscordBot().sendMessage(chatChannelId, formattedMessage);
    }

    /**
     * Build the webhook display username (player name, optionally with PAPI prefix).
     */
    private String buildWebhookUsername(Player player) {
        String format = plugin.getConfigManager().getUsernameFormat();
        if (format == null || format.isEmpty()) {
            return player.getName();
        }
        // Apply PlaceholderAPI if available
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                String expanded = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, format);
                String stripped = MessageFormatter.stripColors(expanded).trim();
                if (!stripped.isEmpty()) {
                    // Discord webhook usernames have a 32 char max
                    return MessageFormatter.truncate(stripped, 32);
                }
            } catch (Exception e) {
                plugin.getPluginLogger().debug("PlaceholderAPI error in webhook username: " + e.getMessage());
            }
        }
        return player.getName();
    }

    /**
     * Format a Minecraft message for Discord display.
     */
    private String formatForDiscord(String message) {
        if (message == null) return "";
        // Strip Minecraft color codes
        if (plugin.getConfigManager().isStripColors()) {
            message = MessageFormatter.stripColors(message);
        }
        // Truncate to Discord's max message length
        message = MessageFormatter.truncate(message, plugin.getConfigManager().getMaxMessageLength());
        return message.trim();
    }

    public void shutdown() {
        if (webhookClient != null) {
            try {
                webhookClient.close();
            } catch (Exception e) {
                plugin.getPluginLogger().debug("Error closing webhook client: " + e.getMessage());
            }
        }
    }

    public AvatarUrlBuilder getAvatarBuilder() {
        return avatarBuilder;
    }

    public boolean isWebhookAvailable() {
        return webhookClient != null;
    }
}
