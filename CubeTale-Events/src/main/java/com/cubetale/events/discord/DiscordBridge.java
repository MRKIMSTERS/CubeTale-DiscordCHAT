package com.cubetale.events.discord;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Bridges CubeTale-Events with CubeTale-DiscordCHAT via reflection.
 * No compile-time dependency — gracefully degrades if DiscordCHAT is absent.
 */
public class DiscordBridge {

    private final CubeTaleEvents plugin;

    // Cached reflection references
    private Object discordChatPlugin;
    private Object discordBot;
    private Method sendEmbed;
    private boolean available = false;

    public DiscordBridge(CubeTaleEvents plugin) {
        this.plugin = plugin;
        hook();
    }

    private void hook() {
        Plugin dc = Bukkit.getPluginManager().getPlugin("CubeTale-DiscordCHAT");
        if (dc == null || !dc.isEnabled()) {
            plugin.getLogger().info("[Events] CubeTale-DiscordCHAT not found — Discord integration disabled.");
            return;
        }
        if (!plugin.getEventsConfig().isDiscordEnabled()) {
            plugin.getLogger().info("[Events] Discord integration disabled in config.");
            return;
        }

        try {
            discordChatPlugin = dc;
            Class<?> pluginClass = dc.getClass();
            Method getBotMethod = pluginClass.getMethod("getDiscordBot");
            discordBot = getBotMethod.invoke(dc);

            if (discordBot == null) {
                plugin.getLogger().warning("[Events] DiscordCHAT bot not connected — Discord events disabled.");
                return;
            }

            // Try to find sendMessageToChannel method
            for (Method m : discordBot.getClass().getMethods()) {
                if (m.getName().equals("sendMessageToChannel")
                        && m.getParameterCount() == 2) {
                    sendEmbed = m;
                    break;
                }
            }

            available = sendEmbed != null;
            if (available) plugin.getLogger().info("[Events] CubeTale-DiscordCHAT hooked successfully.");
            else plugin.getLogger().warning("[Events] CubeTale-DiscordCHAT found but sendMessageToChannel not available.");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Events] Failed to hook into CubeTale-DiscordCHAT: " + e.getMessage());
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void postEventStart(GameEvent event) {
        if (!isReady()) return;
        String color = plugin.getEventsConfig().getDiscordColor("start");
        String msg = buildEmbed("🎉 Event Starting!",
            event.getType().getDisplayName(),
            "Join with `/event join` in-game!\n" + event.getDescription(),
            color);
        sendToChannel(msg);
    }

    public void postEventEnd(GameEvent event) {
        if (!isReady()) return;
        String color = plugin.getEventsConfig().getDiscordColor("end");
        String msg = buildEmbed("✅ Event Ended",
            event.getType().getDisplayName(),
            "The event has concluded. Stay tuned for the next one!",
            color);
        sendToChannel(msg);
    }

    public void postEventWinner(GameEvent event, String first, String second, String third) {
        if (!isReady()) return;
        String color  = plugin.getEventsConfig().getDiscordColor("winner");
        StringBuilder desc = new StringBuilder();
        desc.append("🥇 **").append(first).append("**\n");
        if (second != null && !second.equals("-")) desc.append("🥈 ").append(second).append("\n");
        if (third  != null && !third.equals("-"))  desc.append("🥉 ").append(third);
        String msg = buildEmbed("🏆 Event Winner — " + event.getType().getDisplayName(),
            "Congratulations to the winners!", desc.toString(), color);
        sendToChannel(msg);
    }

    public void postCustomAnnouncement(String title, String description) {
        if (!isReady()) return;
        String color = plugin.getEventsConfig().getDiscordColor("announce");
        sendToChannel(buildEmbed(title, "", description, color));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private void sendToChannel(String message) {
        String channelId = plugin.getEventsConfig().getDiscordChannelId();
        if (channelId.isEmpty() || channelId.equals("EVENTS_CHANNEL_ID")) return;
        try {
            if (sendEmbed != null && discordBot != null) {
                sendEmbed.invoke(discordBot, channelId, MessageUtil.strip(message));
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Events] Failed to send Discord message: " + e.getMessage());
        }
    }

    private String buildEmbed(String title, String subtitle, String description, String hexColor) {
        return title + (subtitle.isEmpty() ? "" : " | " + subtitle) + "\n" + description;
    }

    private boolean isReady() {
        return available && discordBot != null;
    }

    public boolean isAvailable() { return available; }
}
