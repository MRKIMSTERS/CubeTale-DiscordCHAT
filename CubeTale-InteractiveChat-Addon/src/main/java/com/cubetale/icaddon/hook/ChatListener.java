package com.cubetale.icaddon.hook;

import com.cubetale.icaddon.CubeTaleICAddon;
import com.cubetale.icaddon.render.TriggerProcessor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

/**
 * Secondary Bukkit chat listener.
 *
 * This listener marks messages that contain InteractiveChat triggers so the
 * addon is aware of them at the Bukkit level. Processing is intentionally
 * deferred — the IC event in {@link ICEventListener} fires AFTER IC has
 * validated the trigger and is the authoritative dispatch point. This listener
 * exists purely as a compatibility safety net for edge cases where IC's own
 * event doesn't fire (e.g., very old IC builds, test environments without IC).
 *
 * In normal operation (IC + CubeTale-DiscordCHAT both present), the
 * {@link ICEventListener} handles all dispatching and this listener simply
 * passes through without doing any duplicate work.
 */
public class ChatListener implements Listener {

    private final CubeTaleICAddon addon;
    private final TriggerProcessor processor;

    public ChatListener(CubeTaleICAddon addon, DiscordCHATBridge bridge) {
        this.addon     = addon;
        this.processor = new TriggerProcessor(addon, bridge);
    }

    /**
     * Fires at MONITOR priority so CubeTale-DiscordCHAT and InteractiveChat
     * have both had a chance to process the event first.
     *
     * We pass {@code icPrimary = false} to tell the processor this is the
     * fallback path — it will only act if the IC event hasn't already fired.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player  = event.getPlayer();
        String message = event.getMessage();
        if (message == null || message.isEmpty()) return;

        processor.process(player, message, false);
    }
}
