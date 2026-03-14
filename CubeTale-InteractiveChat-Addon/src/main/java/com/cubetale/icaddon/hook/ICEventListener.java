package com.cubetale.icaddon.hook;

import com.cubetale.icaddon.CubeTaleICAddon;
import com.cubetale.icaddon.render.TriggerProcessor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventException;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

/**
 * Registers a listener against InteractiveChat's PrePacketComponentProcessEvent
 * entirely through reflection so that we have zero compile-time dependency on
 * InteractiveChat's classes.
 *
 * InteractiveChat must be loaded before this addon (enforced by plugin.yml
 * depend) so all IC classes are available via Bukkit's plugin classloader.
 *
 * If the reflection registration fails (e.g. an old IC build that doesn't
 * have this event), the addon falls back gracefully to the Bukkit chat
 * listener in {@link ChatListener} and logs a warning instead of crashing.
 */
public class ICEventListener implements Listener {

    private static final String IC_EVENT_CLASS =
            "com.loohp.interactivechat.api.events.PrePacketComponentProcessEvent";

    private final CubeTaleICAddon   addon;
    private final TriggerProcessor  processor;

    public ICEventListener(CubeTaleICAddon addon, DiscordCHATBridge bridge) {
        this.addon     = addon;
        this.processor = new TriggerProcessor(addon, bridge);
    }

    /**
     * Attempts to hook into IC's PrePacketComponentProcessEvent via reflection.
     *
     * @return true if the event was registered successfully, false otherwise.
     */
    public boolean register() {
        try {
            Class<? extends Event> icEventClass =
                    (Class<? extends Event>) Class.forName(IC_EVENT_CLASS);

            Method getSender     = icEventClass.getMethod("getSender");
            Method getRawMessage = icEventClass.getMethod("getRawMessage");

            EventExecutor executor = (listener, event) -> {
                if (!icEventClass.isInstance(event)) return;
                try {
                    Object sender = getSender.invoke(event);
                    if (!(sender instanceof Player)) return;
                    Player player  = (Player) sender;
                    String message = (String) getRawMessage.invoke(event);
                    if (message == null || message.isEmpty()) return;

                    if (addon.getAddonConfig().isDebug()) {
                        addon.getLogger().info("[DEBUG] IC PrePacket event — " + player.getName() + ": " + message);
                    }

                    processor.process(player, message, true);
                } catch (Exception ex) {
                    addon.getLogger().warning("[IC-Addon] Error in IC event handler: " + ex.getMessage());
                }
            };

            addon.getServer().getPluginManager().registerEvent(
                    icEventClass, this, EventPriority.MONITOR, executor, addon, true);

            addon.getLogger().info("Hooked into InteractiveChat PrePacketComponentProcessEvent.");
            return true;

        } catch (ClassNotFoundException e) {
            addon.getLogger().warning("InteractiveChat event class not found — using Bukkit chat listener as fallback.");
            addon.getLogger().warning("Make sure InteractiveChat 4.x is installed.");
            return false;
        } catch (Exception e) {
            addon.getLogger().warning("Failed to hook IC event via reflection: " + e.getMessage());
            return false;
        }
    }
}
