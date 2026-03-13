package com.cubetale.discordchat.discord;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import club.minnced.discord.webhook.send.WebhookEmbed;
import club.minnced.discord.webhook.send.WebhookEmbedBuilder;
import club.minnced.discord.webhook.send.WebhookMessageBuilder;
import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.AvatarUrlBuilder;
import com.cubetale.discordchat.util.MessageFormatter;
import com.cubetale.discordchat.util.MinecraftImageRenderer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class WebhookManager {

    private final CubeTaleDiscordChat plugin;
    private WebhookClient webhookClient;
    private AvatarUrlBuilder avatarBuilder;

    public WebhookManager(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        String service = plugin.getConfigManager().getAvatarService();
        int size       = plugin.getConfigManager().getAvatarSize();
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

    // ── Chat message ─────────────────────────────────────────────────────────────

    /**
     * Send a player chat message via webhook with their Minecraft skin as avatar.
     */
    public void sendPlayerChat(Player player, String message) {
        if (webhookClient == null) {
            sendFallbackBotMessage(player.getName(), message);
            return;
        }

        int size       = plugin.getConfigManager().getAvatarSize();
        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, size);
        String username  = buildWebhookUsername(player);
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

    // ── Item display ──────────────────────────────────────────────────────────────

    /**
     * Renders the player's held item as a Minecraft-style tooltip image and sends it
     * via webhook (using the player's skin avatar + name as the webhook identity).
     *
     * Called from an async context.
     */
    public void sendItemDisplayWebhook(Player player, ItemStack item) {
        if (!plugin.getDiscordBot().isConnected()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);
        String username  = buildWebhookUsername(player);

        try {
            byte[] imageBytes = MinecraftImageRenderer.renderItemTooltip(item, player.getName());
            if (imageBytes == null) return;

            // Build the embed – image lives in the attachment
            String itemName = MinecraftImageRenderer.strip(getItemDisplayName(item));

            WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
                    .setTitle(new WebhookEmbed.EmbedTitle(username + "'s Item", null))
                    .setDescription("**" + itemName + "**")
                    .setColor(0x2f3136)
                    .setImageUrl("attachment://item.png");

            if (webhookClient != null) {
                WebhookMessageBuilder msg = new WebhookMessageBuilder()
                        .setUsername(username)
                        .setAvatarUrl(avatarUrl)
                        .addEmbeds(embed.build())
                        .addFile("item.png", imageBytes);
                webhookClient.send(msg.build());
            } else {
                // Fallback: send via JDA as channel file upload
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                        plugin.getDiscordBot().getJda().getTextChannelById(chatChannelId);
                if (channel == null) return;

                net.dv8tion.jda.api.EmbedBuilder jdaEmbed = new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle(username + "'s Item")
                        .setDescription("**" + itemName + "**")
                        .setColor(0x2f3136)
                        .setImage("attachment://item.png");

                channel.sendFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(imageBytes, "item.png"))
                       .addEmbeds(jdaEmbed.build())
                       .queue();
            }

            plugin.getPluginLogger().debug("Item display sent for " + player.getName() + ": " + itemName);
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to send item display: " + e.getMessage());
        }
    }

    // ── Inventory display ─────────────────────────────────────────────────────────

    /**
     * Renders the player's full inventory as a Minecraft-style grid image and sends
     * it via webhook using the player's skin avatar as the identity.
     * Called from an async context.
     */
    public void sendInventoryWebhook(Player player, ItemStack[] contents) {
        if (!plugin.getDiscordBot().isConnected()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);
        String username  = buildWebhookUsername(player);

        try {
            byte[] imageBytes = MinecraftImageRenderer.renderInventory(contents, player.getName());
            if (imageBytes == null) return;

            WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
                    .setTitle(new WebhookEmbed.EmbedTitle(username + "'s Inventory", null))
                    .setColor(0x2f3136)
                    .setImageUrl("attachment://inventory.png");

            if (webhookClient != null) {
                WebhookMessageBuilder msg = new WebhookMessageBuilder()
                        .setUsername(username)
                        .setAvatarUrl(avatarUrl)
                        .addEmbeds(embed.build())
                        .addFile("inventory.png", imageBytes);
                webhookClient.send(msg.build());
            } else {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                        plugin.getDiscordBot().getJda().getTextChannelById(chatChannelId);
                if (channel == null) return;

                net.dv8tion.jda.api.EmbedBuilder jdaEmbed = new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle(username + "'s Inventory")
                        .setColor(0x2f3136)
                        .setImage("attachment://inventory.png");

                channel.sendFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(imageBytes, "inventory.png"))
                       .addEmbeds(jdaEmbed.build())
                       .queue();
            }

            plugin.getPluginLogger().debug("Inventory display sent for " + player.getName());
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to send inventory display: " + e.getMessage());
        }
    }

    // ── Ender-chest display ───────────────────────────────────────────────────────

    /**
     * Renders the player's ender chest (27 slots) as a Minecraft-style grid image
     * and sends it via webhook.
     * Called from an async context.
     */
    public void sendEnderChestWebhook(Player player, ItemStack[] contents) {
        if (!plugin.getDiscordBot().isConnected()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);
        String username  = buildWebhookUsername(player);

        try {
            byte[] imageBytes = MinecraftImageRenderer.renderInventory(contents, player.getName());
            if (imageBytes == null) return;

            WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
                    .setTitle(new WebhookEmbed.EmbedTitle(username + "'s Ender Chest", null))
                    .setColor(0x660066)
                    .setImageUrl("attachment://enderchest.png");

            if (webhookClient != null) {
                WebhookMessageBuilder msg = new WebhookMessageBuilder()
                        .setUsername(username)
                        .setAvatarUrl(avatarUrl)
                        .addEmbeds(embed.build())
                        .addFile("enderchest.png", imageBytes);
                webhookClient.send(msg.build());
            } else {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                        plugin.getDiscordBot().getJda().getTextChannelById(chatChannelId);
                if (channel == null) return;

                net.dv8tion.jda.api.EmbedBuilder jdaEmbed = new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle(username + "'s Ender Chest")
                        .setColor(0x660066)
                        .setImage("attachment://enderchest.png");

                channel.sendFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(imageBytes, "enderchest.png"))
                       .addEmbeds(jdaEmbed.build())
                       .queue();
            }

            plugin.getPluginLogger().debug("Ender chest display sent for " + player.getName());
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to send ender chest display: " + e.getMessage());
        }
    }

    // ── Armor display ─────────────────────────────────────────────────────────────

    /**
     * Renders a player's equipped armor (helmet/chestplate/leggings/boots) plus offhand
     * as a vertical tooltip strip and sends it via webhook.
     */
    public void sendArmorWebhook(Player player, ItemStack[] armorSlots, ItemStack offhand) {
        if (!plugin.getDiscordBot().isConnected()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);
        String username  = buildWebhookUsername(player);

        try {
            byte[] imageBytes = MinecraftImageRenderer.renderArmor(armorSlots, offhand, player.getName());
            if (imageBytes == null) return;

            WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
                    .setTitle(new WebhookEmbed.EmbedTitle(username + "'s Armor", null))
                    .setColor(0x4444AA)
                    .setImageUrl("attachment://armor.png");

            if (webhookClient != null) {
                WebhookMessageBuilder msg = new WebhookMessageBuilder()
                        .setUsername(username)
                        .setAvatarUrl(avatarUrl)
                        .addEmbeds(embed.build())
                        .addFile("armor.png", imageBytes);
                webhookClient.send(msg.build());
            } else {
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                        plugin.getDiscordBot().getJda().getTextChannelById(chatChannelId);
                if (channel == null) return;
                net.dv8tion.jda.api.EmbedBuilder jdaEmbed = new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle(username + "'s Armor")
                        .setColor(0x4444AA)
                        .setImage("attachment://armor.png");
                channel.sendFiles(net.dv8tion.jda.api.utils.FileUpload.fromData(imageBytes, "armor.png"))
                       .addEmbeds(jdaEmbed.build()).queue();
            }
            plugin.getPluginLogger().debug("Armor display sent for " + player.getName());
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to send armor display: " + e.getMessage());
        }
    }

    // ── Map display ────────────────────────────────────────────────────────────────

    /**
     * Shows the map item the player is holding as a Discord embed with name + map ID.
     */
    public void sendMapWebhook(Player player, ItemStack mapItem) {
        if (!plugin.getDiscordBot().isConnected()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);
        String username  = buildWebhookUsername(player);

        String mapName = "Map";
        String mapId   = "";
        try {
            org.bukkit.inventory.meta.MapMeta meta =
                    (org.bukkit.inventory.meta.MapMeta) mapItem.getItemMeta();
            if (meta != null) {
                if (meta.hasDisplayName()) mapName = MinecraftImageRenderer.strip(meta.getDisplayName());
                if (meta.hasMapId()) mapId = " (ID: " + meta.getMapId() + ")";
            }
        } catch (Exception ignored) {}

        String description = "**" + username + "** is holding a map: **" + mapName + "**" + mapId;

        net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                .setTitle("🗺 " + mapName + mapId)
                .setDescription(description)
                .setColor(0x8B5E3C)
                .setThumbnail("https://mc.nerothe.com/img/1.21/filled_map.png");

        if (webhookClient != null) {
            WebhookEmbedBuilder wb = new WebhookEmbedBuilder()
                    .setTitle(new WebhookEmbed.EmbedTitle("🗺 " + mapName + mapId, null))
                    .setDescription(description)
                    .setColor(0x8B5E3C);
            WebhookMessageBuilder msg = new WebhookMessageBuilder()
                    .setUsername(username)
                    .setAvatarUrl(avatarUrl)
                    .addEmbeds(wb.build());
            webhookClient.send(msg.build());
        } else {
            net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                    plugin.getDiscordBot().getJda().getTextChannelById(chatChannelId);
            if (channel != null) channel.sendMessageEmbeds(embed.build()).queue();
        }
        plugin.getPluginLogger().debug("Map display sent for " + player.getName());
    }

    // ── Book display ───────────────────────────────────────────────────────────────

    /**
     * Shows the first few pages of a written/writable book as a Discord embed.
     */
    public void sendBookWebhook(Player player, ItemStack bookItem) {
        if (!plugin.getDiscordBot().isConnected()) return;
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;

        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);
        String username  = buildWebhookUsername(player);

        String title  = "Book";
        String author = "";
        StringBuilder pages = new StringBuilder();
        int pageCount = 0;

        try {
            if (bookItem.getItemMeta() instanceof org.bukkit.inventory.meta.BookMeta) {
                org.bukkit.inventory.meta.BookMeta meta =
                        (org.bukkit.inventory.meta.BookMeta) bookItem.getItemMeta();
                if (meta.hasTitle()) title  = MinecraftImageRenderer.strip(meta.getTitle());
                if (meta.hasAuthor()) author = meta.getAuthor();
                pageCount = meta.getPageCount();
                int limit = Math.min(pageCount, 3);
                for (int i = 1; i <= limit; i++) {
                    String pageText = MinecraftImageRenderer.strip(meta.getPage(i));
                    if (pageText.length() > 300) pageText = pageText.substring(0, 300) + "…";
                    pages.append("**Page ").append(i).append("**\n").append(pageText).append("\n\n");
                }
                if (pageCount > 3) pages.append("*…and ").append(pageCount - 3).append(" more page(s)*");
            }
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Could not read book content: " + e.getMessage());
        }

        if (pages.length() == 0) pages.append("*(empty)*");

        String embedTitle = "📖 " + title + (author.isEmpty() ? "" : " by " + author);
        String body       = pages.toString();

        if (webhookClient != null) {
            WebhookEmbedBuilder wb = new WebhookEmbedBuilder()
                    .setTitle(new WebhookEmbed.EmbedTitle(embedTitle, null))
                    .setDescription(body)
                    .setColor(0xD4A96A);
            WebhookMessageBuilder msg = new WebhookMessageBuilder()
                    .setUsername(username)
                    .setAvatarUrl(avatarUrl)
                    .addEmbeds(wb.build());
            webhookClient.send(msg.build());
        } else {
            net.dv8tion.jda.api.entities.channel.concrete.TextChannel channel =
                    plugin.getDiscordBot().getJda().getTextChannelById(chatChannelId);
            if (channel != null) {
                net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder()
                        .setTitle(embedTitle).setDescription(body).setColor(0xD4A96A);
                channel.sendMessageEmbeds(embed.build()).queue();
            }
        }
        plugin.getPluginLogger().debug("Book display sent for " + player.getName());
    }

    // ── Advancement (webhook embed with player avatar + icon thumbnail) ───────────

    /**
     * Sends an advancement notification via webhook.
     * The webhook uses the player's skin as its avatar, and the advancement item
     * icon appears as the embed thumbnail on the right-hand side.
     */
    public void sendAdvancementWebhook(Player player, String advancementTitle,
                                       String description, String iconUrl) {
        if (!plugin.getDiscordBot().isConnected()) return;

        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);
        String username  = buildWebhookUsername(player);
        String body      = "**" + player.getName() + "** has made the advancement **"
                           + advancementTitle + "**";
        if (description != null && !description.isEmpty()) {
            body += "\n*" + description + "*";
        }

        // Discord webhook embeds support thumbnails via setThumbnailUrl
        WebhookEmbedBuilder embed = new WebhookEmbedBuilder()
                .setDescription(body)
                .setColor(0x55FF55); // bright Minecraft green

        if (iconUrl != null && !iconUrl.isEmpty()) {
            embed.setThumbnailUrl(iconUrl);
        }

        try {
            if (webhookClient != null) {
                WebhookMessageBuilder msg = new WebhookMessageBuilder()
                        .setUsername(username)
                        .setAvatarUrl(avatarUrl)
                        .addEmbeds(embed.build());
                webhookClient.send(msg.build());
            } else {
                // Fallback: send via JDA bot embed
                plugin.getDiscordBot().sendAdvancementNotification(
                        player.getName(), player.getUniqueId().toString(),
                        advancementTitle, description, avatarUrl, iconUrl);
            }
            plugin.getPluginLogger().debug("Advancement webhook sent for " + player.getName());
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to send advancement webhook: " + e.getMessage());
        }
    }

    // ── Fallback ─────────────────────────────────────────────────────────────────

    private void sendFallbackBotMessage(String playerName, String message) {
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;
        if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) return;

        String formattedMessage = "**" + playerName + "**: " + formatForDiscord(message);
        plugin.getDiscordBot().sendMessage(chatChannelId, formattedMessage);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────────

    private String buildWebhookUsername(Player player) {
        String format = plugin.getConfigManager().getUsernameFormat();
        if (format == null || format.isEmpty()) {
            return player.getName();
        }
        if (plugin.getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                String expanded = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, format);
                String stripped = MessageFormatter.stripColors(expanded).trim();
                if (!stripped.isEmpty()) {
                    return MessageFormatter.truncate(stripped, 32);
                }
            } catch (Exception e) {
                plugin.getPluginLogger().debug("PlaceholderAPI error in webhook username: " + e.getMessage());
            }
        }
        return player.getName();
    }

    private String formatForDiscord(String message) {
        if (message == null) return "";
        if (plugin.getConfigManager().isStripColors()) {
            message = MessageFormatter.stripColors(message);
        }
        message = MessageFormatter.truncate(message, plugin.getConfigManager().getMaxMessageLength());
        return message.trim();
    }

    private static String getItemDisplayName(ItemStack item) {
        if (item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String key = item.getType().getKey().getKey().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : key.split(" ")) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(' ');
            }
        }
        return sb.toString().trim();
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
