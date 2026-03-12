package com.cubetale.discordchat.minecraft;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.MessageFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatHandler implements Listener {

    private final CubeTaleDiscordChat plugin;

    public ChatHandler(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    /**
     * Listen to Minecraft player chat and forward to Discord via webhook.
     * Uses AsyncPlayerChatEvent for compatibility with Spigot.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfigManager().isChatSyncEnabled()) return;
        if (!plugin.getConfigManager().isMinecraftToDiscordEnabled()) return;

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Check message length
        if (message.length() > plugin.getConfigManager().getMaxMessageLength()) {
            message = MessageFormatter.truncate(message, plugin.getConfigManager().getMaxMessageLength());
        }

        // Check for links
        if (!plugin.getConfigManager().isLinksAllowed()) {
            if (MessageFormatter.containsUrl(message)) {
                plugin.getPluginLogger().debug("Blocked message with URL from " + player.getName());
                return;
            }
        }

        final String finalMessage = message;

        // Send to Discord via webhook (already on async thread)
        plugin.getWebhookManager().sendPlayerChat(player, finalMessage);
        plugin.getPluginLogger().debug("Chat forwarded to Discord for " + player.getName());
    }
}
