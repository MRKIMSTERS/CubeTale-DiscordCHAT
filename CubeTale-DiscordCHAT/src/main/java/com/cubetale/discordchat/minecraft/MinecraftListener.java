package com.cubetale.discordchat.minecraft;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.MessageFormatter;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MinecraftListener implements Listener {

    private final CubeTaleDiscordChat plugin;

    public MinecraftListener(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!plugin.getDiscordBot().isConnected()) return;

        Player player = event.getPlayer();
        String playerName = player.getName();
        String playerUUID = player.getUniqueId().toString();

        // Run async to avoid blocking the main thread
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendPlayerJoinNotification(playerName, playerUUID);
            plugin.getPluginLogger().debug("Join notification sent for " + playerName);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getDiscordBot().isConnected()) return;

        Player player = event.getPlayer();
        String playerName = player.getName();
        String playerUUID = player.getUniqueId().toString();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendPlayerLeaveNotification(playerName, playerUUID);
            plugin.getPluginLogger().debug("Leave notification sent for " + playerName);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!plugin.getDiscordBot().isConnected()) return;
        if (!plugin.getConfigManager().isDeathEnabled()) return;

        Player player = event.getEntity();
        String playerName = player.getName();
        String playerUUID = player.getUniqueId().toString();

        String deathMessage = event.getDeathMessage() != null
                ? MessageFormatter.stripColors(event.getDeathMessage())
                : playerName + " died.";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendPlayerDeathNotification(playerName, playerUUID, deathMessage);
            plugin.getPluginLogger().debug("Death notification sent for " + playerName);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerAdvancement(PlayerAdvancementDoneEvent event) {
        if (!plugin.getDiscordBot().isConnected()) return;
        if (!plugin.getConfigManager().isAdvancementEnabled()) return;

        Advancement advancement = event.getAdvancement();
        AdvancementDisplay display = advancement.getDisplay();

        // Only notify for advancements that have a visible display
        if (display == null || !display.shouldAnnounceChat()) return;

        Player player = event.getPlayer();
        String playerName = player.getName();
        String playerUUID = player.getUniqueId().toString();

        String advancementTitle = display.getTitle();
        String advancementDesc = display.getDescription();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendAdvancementNotification(
                    playerName, playerUUID, advancementTitle, advancementDesc);
            plugin.getPluginLogger().debug("Advancement notification sent for " + playerName + ": " + advancementTitle);
        });
    }
}
