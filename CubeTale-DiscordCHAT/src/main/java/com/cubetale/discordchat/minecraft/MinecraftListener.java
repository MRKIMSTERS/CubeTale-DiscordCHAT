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
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

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
        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendPlayerJoinNotification(playerName, playerUUID, avatarUrl);
            plugin.getPluginLogger().debug("Join notification sent for " + playerName);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!plugin.getDiscordBot().isConnected()) return;

        Player player = event.getPlayer();
        String playerName = player.getName();
        String playerUUID = player.getUniqueId().toString();
        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendPlayerLeaveNotification(playerName, playerUUID, avatarUrl);
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
        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);

        String deathMessage = event.getDeathMessage() != null
                ? MessageFormatter.stripColors(event.getDeathMessage())
                : playerName + " died.";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendPlayerDeathNotification(playerName, playerUUID, deathMessage, avatarUrl);
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
        String avatarUrl = plugin.getSkinsRestorerHook().resolveAvatarUrl(player, 128);

        String advancementTitle = display.getTitle();
        String advancementDesc = display.getDescription();

        // Try to get the advancement item icon (Paper API, may not be available on plain Spigot)
        String iconUrl = resolveAdvancementIconUrl(display);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            plugin.getDiscordBot().sendAdvancementNotification(
                    playerName, playerUUID, advancementTitle, advancementDesc, avatarUrl, iconUrl);
            plugin.getPluginLogger().debug("Advancement notification sent for " + playerName + ": " + advancementTitle);
        });
    }

    /**
     * Attempts to retrieve the item icon from an AdvancementDisplay via the Paper API.
     * Falls back gracefully to null on plain Spigot where the method is not present.
     *
     * The item key is mapped to a texture CDN URL so Discord can embed it as an image.
     *
     * @param display  The advancement display
     * @return A CDN image URL for the icon, or null if unavailable
     */
    private String resolveAdvancementIconUrl(AdvancementDisplay display) {
        try {
            Method getIcon = display.getClass().getMethod("getIcon");
            ItemStack icon = (ItemStack) getIcon.invoke(display);
            if (icon == null) return null;

            // material key: e.g. "diamond_sword", "oak_log", "shield"
            String materialKey = icon.getType().getKey().getKey().toLowerCase();
            // mc.nerothe.com serves both item and block textures indexed by version
            return "https://mc.nerothe.com/img/1.21/" + materialKey + ".png";
        } catch (NoSuchMethodException ignored) {
            // Plain Spigot — getIcon() not in API, silently skip
        } catch (Exception e) {
            plugin.getPluginLogger().debug("Could not resolve advancement icon: " + e.getMessage());
        }
        return null;
    }
}
