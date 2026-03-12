package com.cubetale.discordchat.minecraft;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.MessageFormatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

public class ChatHandler implements Listener {

    private final CubeTaleDiscordChat plugin;

    public ChatHandler(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    /**
     * Listen to Minecraft player chat and forward to Discord via webhook.
     * Uses AsyncPlayerChatEvent for compatibility with Spigot.
     *
     * Special trigger: if the player includes "[item]" anywhere in their message,
     * the plugin also renders their held item as a Minecraft-style tooltip image
     * and sends it to Discord.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfigManager().isChatSyncEnabled()) return;
        if (!plugin.getConfigManager().isMinecraftToDiscordEnabled()) return;

        Player player  = event.getPlayer();
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

        // Detect [item] keyword (case-insensitive)
        if (containsItemTrigger(message)) {
            // We need the main thread to safely read inventory; schedule sync task
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() != Material.AIR) {
                    // Clone so async thread gets a stable copy
                    ItemStack itemCopy = held.clone();
                    // Go async for rendering + sending
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getWebhookManager().sendItemDisplayWebhook(player, itemCopy);
                        plugin.getPluginLogger().debug(
                                "Item display sent for " + player.getName()
                                + ": " + itemCopy.getType().getKey());
                    });
                }
            });
        }

        // Always forward the original chat message
        plugin.getWebhookManager().sendPlayerChat(player, finalMessage);
        plugin.getPluginLogger().debug("Chat forwarded to Discord for " + player.getName());
    }

    /** Returns true if the message contains [item] in any case. */
    private static boolean containsItemTrigger(String message) {
        return message.toLowerCase().contains("[item]");
    }
}
