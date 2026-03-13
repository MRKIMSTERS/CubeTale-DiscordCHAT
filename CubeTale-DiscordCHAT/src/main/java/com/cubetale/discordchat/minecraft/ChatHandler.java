package com.cubetale.discordchat.minecraft;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.InteractiveChatHook;
import com.cubetale.discordchat.util.MessageFormatter;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Listens to Minecraft player chat and forwards messages to Discord.
 *
 * Interactive-render triggers (works standalone AND when InteractiveChat is installed):
 *   [item]        — renders the player's held item as a Minecraft tooltip image
 *   [inv]         — renders the player's full inventory as a grid image
 *   [enderchest]  — renders the player's ender chest contents as a grid image
 *   [ec]          — alias for [enderchest]
 *
 * When InteractiveChat is installed, any additional IC-registered custom placeholders
 * are also detected and stripped from the Discord-bound text message.
 */
public class ChatHandler implements Listener {

    private final CubeTaleDiscordChat plugin;

    public ChatHandler(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (!plugin.getConfigManager().isChatSyncEnabled()) return;
        if (!plugin.getConfigManager().isMinecraftToDiscordEnabled()) return;

        Player player = event.getPlayer();
        String message = event.getMessage();

        // Enforce max length
        if (message.length() > plugin.getConfigManager().getMaxMessageLength()) {
            message = MessageFormatter.truncate(message, plugin.getConfigManager().getMaxMessageLength());
        }

        // Block bare URLs if configured
        if (!plugin.getConfigManager().isLinksAllowed() && MessageFormatter.containsUrl(message)) {
            plugin.getPluginLogger().debug("Blocked message with URL from " + player.getName());
            return;
        }

        final String rawMessage = message;
        final InteractiveChatHook ic = plugin.getInteractiveChatHook();

        // ── [item] ────────────────────────────────────────────────────────────
        if (InteractiveChatHook.hasTrigger(rawMessage, InteractiveChatHook.TRIGGER_ITEM)) {
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

        // ── [inv] ─────────────────────────────────────────────────────────────
        if (InteractiveChatHook.hasTrigger(rawMessage, InteractiveChatHook.TRIGGER_INV)
                || InteractiveChatHook.hasTrigger(rawMessage, "[inventory]")) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack[] full = new ItemStack[41];
                ItemStack[] main  = player.getInventory().getContents();
                ItemStack[] armor = player.getInventory().getArmorContents();
                ItemStack[] extra = player.getInventory().getExtraContents();
                System.arraycopy(main, 0, full, 0, Math.min(main.length, 36));
                if (armor != null)
                    for (int i = 0; i < Math.min(armor.length, 4); i++) full[36 + i] = armor[i];
                if (extra != null && extra.length > 0) full[40] = extra[0];

                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getWebhookManager().sendInventoryWebhook(player, full);
                    plugin.getPluginLogger().debug("[inv] sent for " + player.getName());
                });
            });
        }

        // ── [enderchest] / [ec] ───────────────────────────────────────────────
        if (InteractiveChatHook.hasTrigger(rawMessage, InteractiveChatHook.TRIGGER_ENDERCHEST)
                || InteractiveChatHook.hasTrigger(rawMessage, InteractiveChatHook.TRIGGER_EC_SHORT)) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack[] ec = player.getEnderChest().getContents().clone();
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getWebhookManager().sendEnderChestWebhook(player, ec);
                    plugin.getPluginLogger().debug("[enderchest] sent for " + player.getName());
                });
            });
        }

        // ── Forward to Discord — strip all IC trigger tokens first ─────────────
        // Removing [item] / [inv] / [enderchest] and any custom IC placeholders
        // keeps the Discord message clean. If the whole message was just a trigger
        // (e.g. someone typed only "[item]") the image embed is the full context,
        // so we skip forwarding an empty string.
        String discordText = ic.stripTriggers(rawMessage);
        if (!discordText.isEmpty()) {
            plugin.getWebhookManager().sendPlayerChat(player, discordText);
            plugin.getPluginLogger().debug("Chat forwarded to Discord for " + player.getName());
        }
    }
}
