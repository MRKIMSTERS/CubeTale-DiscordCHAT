package com.cubetale.icaddon.hook;

import com.cubetale.icaddon.CubeTaleICAddon;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * Reflection-based bridge to CubeTale-DiscordCHAT's WebhookManager.
 *
 * We call all methods via reflection so this addon has zero compile-time
 * dependency on CubeTale-DiscordCHAT's classes (or its transitive libraries
 * such as JDA). At runtime both plugins share Bukkit's classloader, so the
 * actual method calls resolve fine.
 *
 * Bridge call chain:
 *   Bukkit plugin manager → "CubeTale-DiscordCHAT" plugin instance
 *     → getWebhookManager() → WebhookManager
 *       → send*Webhook(player, ...) methods
 */
public class DiscordCHATBridge {

    private static final String PLUGIN_NAME = "CubeTale-DiscordCHAT";

    private final CubeTaleICAddon addon;

    private Object pluginInstance;
    private Object webhookManager;

    private Method methodGetWebhookManager;
    private Method methodGetDiscordBot;
    private Method methodIsConnected;
    private Method methodIsWebhookEnabled;
    private Method methodGetConfigManager;

    private Method methodSendItemDisplay;
    private Method methodSendInventory;
    private Method methodSendEnderChest;
    private Method methodSendArmor;
    private Method methodSendOffhand;
    private Method methodSendMap;
    private Method methodSendBook;

    private boolean ready = false;

    public DiscordCHATBridge(CubeTaleICAddon addon) {
        this.addon = addon;
        connect();
    }

    private void connect() {
        Plugin found = addon.getServer().getPluginManager().getPlugin(PLUGIN_NAME);
        if (found == null || !found.isEnabled()) {
            addon.getLogger().severe(PLUGIN_NAME + " plugin not found or not enabled.");
            return;
        }
        pluginInstance = found;

        try {
            Class<?> pluginClass = found.getClass();

            methodGetWebhookManager = pluginClass.getMethod("getWebhookManager");
            methodGetDiscordBot     = pluginClass.getMethod("getDiscordBot");
            methodGetConfigManager  = pluginClass.getMethod("getConfigManager");

            Object wm = methodGetWebhookManager.invoke(pluginInstance);
            if (wm == null) {
                addon.getLogger().severe("WebhookManager is null in " + PLUGIN_NAME + ".");
                return;
            }
            webhookManager = wm;
            Class<?> wmClass = wm.getClass();

            methodSendItemDisplay = wmClass.getMethod("sendItemDisplayWebhook", Player.class, ItemStack.class);
            methodSendInventory   = wmClass.getMethod("sendInventoryWebhook",   Player.class, ItemStack[].class);
            methodSendEnderChest  = wmClass.getMethod("sendEnderChestWebhook",  Player.class, ItemStack[].class);
            methodSendArmor       = wmClass.getMethod("sendArmorWebhook",       Player.class, ItemStack[].class, ItemStack.class);
            methodSendMap         = wmClass.getMethod("sendMapWebhook",         Player.class, ItemStack.class);
            methodSendBook        = wmClass.getMethod("sendBookWebhook",        Player.class, ItemStack.class);

            Object configManager = methodGetConfigManager.invoke(pluginInstance);
            methodIsWebhookEnabled = configManager.getClass().getMethod("isWebhookEnabled");

            ready = true;
            addon.getLogger().info("Successfully hooked into " + PLUGIN_NAME + " (reflection bridge).");

        } catch (Exception e) {
            addon.getLogger().severe("Failed to hook " + PLUGIN_NAME + " via reflection: " + e.getMessage());
            ready = false;
        }
    }

    public boolean isReady() {
        return ready && pluginInstance != null && ((Plugin) pluginInstance).isEnabled();
    }

    public boolean isDiscordConnected() {
        if (!isReady()) return false;
        try {
            Object bot = methodGetDiscordBot.invoke(pluginInstance);
            if (bot == null) return false;
            Method connected = bot.getClass().getMethod("isConnected");
            return (Boolean) connected.invoke(bot);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isWebhookEnabled() {
        if (!isReady()) return false;
        try {
            Object cfg = methodGetConfigManager.invoke(pluginInstance);
            return (Boolean) methodIsWebhookEnabled.invoke(cfg);
        } catch (Exception e) {
            return false;
        }
    }

    public void sendItemDisplay(Player player, ItemStack item) {
        invoke("sendItemDisplay", methodSendItemDisplay, player, item);
    }

    public void sendInventory(Player player, ItemStack[] contents) {
        invoke("sendInventory", methodSendInventory, player, new Object[]{contents});
    }

    public void sendEnderChest(Player player, ItemStack[] contents) {
        invoke("sendEnderChest", methodSendEnderChest, player, new Object[]{contents});
    }

    public void sendArmor(Player player, ItemStack[] armorSlots, ItemStack offhand) {
        invoke("sendArmor", methodSendArmor, player, armorSlots, offhand);
    }

    public void sendOffhand(Player player, ItemStack offhand) {
        invoke("sendOffhand", methodSendItemDisplay, player, offhand);
    }

    public void sendMap(Player player, ItemStack mapItem) {
        invoke("sendMap", methodSendMap, player, mapItem);
    }

    public void sendBook(Player player, ItemStack bookItem) {
        invoke("sendBook", methodSendBook, player, bookItem);
    }

    private void invoke(String name, Method method, Object... args) {
        if (!isReady() || method == null) return;
        try {
            method.invoke(webhookManager, args);
            if (addon.getAddonConfig().isDebug()) {
                addon.getLogger().info("[DEBUG] Dispatched " + name + " for " + args[0]);
            }
        } catch (Exception e) {
            addon.getLogger().warning("[IC-Addon] Bridge call " + name + " failed: " + e.getMessage());
        }
    }
}
