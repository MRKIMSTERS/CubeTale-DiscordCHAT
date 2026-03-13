package com.cubetale.discordchat.minecraft;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.MessageFormatter;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;

public class MinecraftListener implements Listener {

    private final CubeTaleDiscordChat plugin;

    public MinecraftListener(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getDiscordBot().isConnected()) return;

        Player player      = event.getPlayer();
        String playerName  = player.getName();
        String playerUUID  = player.getUniqueId().toString();
        boolean firstJoin  = !player.hasPlayedBefore();
        String avatarUrl   = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (firstJoin) {
                plugin.getDiscordBot().sendFirstJoinNotification(playerName, playerUUID, avatarUrl);
            } else {
                plugin.getDiscordBot().sendPlayerJoinNotification(playerName, playerUUID, avatarUrl);
            }
            plugin.getPluginLogger().debug((firstJoin ? "First-join" : "Join") + " notification sent for " + playerName);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getDiscordBot().isConnected()) return;

        Player player      = event.getPlayer();
        String playerName  = player.getName();
        String playerUUID  = player.getUniqueId().toString();
        String avatarUrl   = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendPlayerLeaveNotification(playerName, playerUUID, avatarUrl);
            plugin.getPluginLogger().debug("Leave notification sent for " + playerName);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getDiscordBot().isConnected()) return;

        Player victim     = event.getEntity();
        String victimName = victim.getName();
        String victimUUID = victim.getUniqueId().toString();
        String victimAvatar = plugin.getSkinsRestorerHook().resolveAvatarUrl(victim, 128);

        // Check for PvP kill
        Entity killerEntity = victim.getKiller();
        if (killerEntity instanceof Player) {
            Player killer = (Player) killerEntity;
            if (plugin.getConfigManager().isPvpKillEnabled()) {
                String killerName   = killer.getName();
                String killerUUID   = killer.getUniqueId().toString();
                String killerAvatar = plugin.getSkinsRestorerHook().resolveAvatarUrl(killer, 128);
                ItemStack weapon    = killer.getInventory().getItemInMainHand();
                String weaponName   = resolveItemName(weapon);

                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    plugin.getDiscordBot().sendPvpKillNotification(
                            killerName, killerUUID, killerAvatar, victimName, weaponName);
                    plugin.getPluginLogger().debug("PvP kill notification: " + killerName + " -> " + victimName);
                });
            }
        }

        // Regular death notification
        if (!plugin.getConfigManager().isDeathEnabled()) return;

        String deathMessage = event.getDeathMessage() != null
                ? MessageFormatter.stripColors(event.getDeathMessage())
                : victimName + " died.";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendPlayerDeathNotification(victimName, victimUUID, deathMessage, victimAvatar);
            plugin.getPluginLogger().debug("Death notification sent for " + victimName);
        });
    }

    private String resolveItemName(ItemStack item) {
        if (item == null || item.getType().isAir()) return "";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return MessageFormatter.stripColors(meta.getDisplayName());
        String key = item.getType().getKey().getKey().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String w : key.split(" ")) {
            if (!w.isEmpty()) sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getDiscordBot().isConnected()) return;
        if (!plugin.getConfigManager().isAdvancementEnabled()) return;

        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();

        // Only notify for advancements that have a visible display
        if (display == null || !display.shouldAnnounceChat()) return;

        Player player          = event.getPlayer();
        String advancementTitle = display.getTitle();
        String advancementDesc  = display.getDescription();

        // Try to get the advancement item icon (Paper API only; graceful fallback on Spigot)
        String iconUrl = resolveAdvancementIconUrl(display);

        // Send via webhook so the player's skin appears as the message avatar
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getWebhookManager().sendAdvancementWebhook(
                    player, advancementTitle, advancementDesc, iconUrl);
            plugin.getPluginLogger().debug("Advancement webhook sent for "
                    + player.getName() + ": " + advancementTitle);
        });
    }

    /**
     * Attempts to retrieve the item icon from an AdvancementDisplay via the Paper API.
     * Falls back gracefully to null on plain Spigot where the method is not present.
     */
    private String resolveAdvancementIconUrl(AdvancementDisplay display) {
        try {
            Method getIcon = display.getClass().getMethod("getIcon");
            ItemStack icon = (ItemStack) getIcon.invoke(display);
            if (icon == null) return null;

            String materialKey = icon.getType().getKey().getKey().toLowerCase();
            return "https://mc.nerothe.com/img/1.21/" + materialKey + ".png";
        } catch (NoSuchMethodException ignored) {
            // Plain Spigot — getIcon() not in API
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Could not resolve advancement icon: " + e.getMessage());
        }
        return null;
    }
}
