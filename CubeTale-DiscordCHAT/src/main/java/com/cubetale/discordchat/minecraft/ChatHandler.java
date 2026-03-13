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

        // ── [item] trigger ────────────────────────────────────────────────────
        if (containsTrigger(message, "[item]")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() != Material.AIR) {
                    ItemStack copy = held.clone();
                    plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                        plugin.getWebhookManager().sendItemDisplayWebhook(player, copy);
                        plugin.getPluginLogger().debug(
                                "[item] sent for " + player.getName() + ": " + copy.getType().getKey());
                    });
                }
            });
        }

        // ── [inv] trigger ─────────────────────────────────────────────────────
        if (containsTrigger(message, "[inv]")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                // Build a full 41-slot array: 0-35 main+hotbar, 36-39 armor, 40 offhand
                ItemStack[] full = new ItemStack[41];
                ItemStack[] main = player.getInventory().getContents();
                System.arraycopy(main, 0, full, 0, Math.min(main.length, 36));
                ItemStack[] armor = player.getInventory().getArmorContents();
                if (armor != null) {
                    for (int i = 0; i < Math.min(armor.length, 4); i++) full[36 + i] = armor[i];
                }
                ItemStack[] extra = player.getInventory().getExtraContents();
                if (extra != null && extra.length > 0) full[40] = extra[0];
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getWebhookManager().sendInventoryWebhook(player, full);
                    plugin.getPluginLogger().debug("[inv] sent for " + player.getName());
                });
            });
        }

        // Always forward the original chat message
        plugin.getWebhookManager().sendPlayerChat(player, finalMessage);
        plugin.getPluginLogger().debug("Chat forwarded to Discord for " + player.getName());
    }

    private static boolean containsTrigger(String message, String trigger) {
        return message.toLowerCase().contains(trigger);
    }
}
