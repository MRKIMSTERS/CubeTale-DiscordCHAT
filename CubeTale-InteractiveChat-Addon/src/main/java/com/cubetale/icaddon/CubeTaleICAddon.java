package com.cubetale.icaddon;

import com.cubetale.icaddon.config.AddonConfig;
import com.cubetale.icaddon.hook.DiscordCHATBridge;
import com.cubetale.icaddon.hook.ICEventListener;
import com.cubetale.icaddon.hook.ChatListener;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CubeTale-InteractiveChat-Addon
 *
 * A bridge plugin that connects InteractiveChat (LOOHP) with CubeTale-DiscordCHAT.
 * When a player types a placeholder such as [item], [inv], [enderchest], [armor],
 * [offhand], [map], or [book] in Minecraft chat, this addon intercepts the trigger
 * and sends a rendered image / rich embed to Discord via CubeTale-DiscordCHAT's
 * webhook system.
 *
 * No DiscordSRV required — works exclusively with CubeTale-DiscordCHAT.
 */
public class CubeTaleICAddon extends JavaPlugin {

    private static CubeTaleICAddon instance;

    private AddonConfig addonConfig;
    private DiscordCHATBridge bridge;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        addonConfig = new AddonConfig(this);

        bridge = new DiscordCHATBridge(this);
        if (!bridge.isReady()) {
            getLogger().severe("CubeTale-DiscordCHAT is not available or not ready.");
            getLogger().severe("Make sure CubeTale-DiscordCHAT is installed and loaded before this addon.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        ICEventListener icListener = new ICEventListener(this, bridge);
        boolean icHooked = icListener.register();

        if (!icHooked) {
            getLogger().warning("IC event hook failed — falling back to Bukkit chat event.");
        }

        getServer().getPluginManager().registerEvents(new ChatListener(this, bridge), this);

        getLogger().info("CubeTale-InteractiveChat-Addon v" + getDescription().getVersion() + " enabled.");
        getLogger().info("IC event hook: " + (icHooked ? "active" : "fallback") + " | Webhook: " + bridge.isWebhookEnabled());
    }

    @Override
    public void onDisable() {
        getLogger().info("CubeTale-InteractiveChat-Addon disabled.");
        instance = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("ctdicaddon")) return false;

        if (!sender.hasPermission("cubetale.icaddon.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6[CubeTale-IC-Addon] §fVersion: §a" + getDescription().getVersion());
            sender.sendMessage("§6[CubeTale-IC-Addon] §fBridge ready: §a" + bridge.isReady());
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                reloadConfig();
                addonConfig.reload();
                sender.sendMessage("§6[CubeTale-IC-Addon] §aConfiguration reloaded.");
                break;

            case "status":
                sender.sendMessage("§6[CubeTale-IC-Addon] §fBridge ready: §a" + bridge.isReady());
                sender.sendMessage("§6[CubeTale-IC-Addon] §fDiscordCHAT connected: §a" + bridge.isDiscordConnected());
                sender.sendMessage("§6[CubeTale-IC-Addon] §fWebhook enabled: §a" + bridge.isWebhookEnabled());
                break;

            default:
                sender.sendMessage("§cUsage: /" + label + " [reload|status]");
        }
        return true;
    }

    public static CubeTaleICAddon getInstance() {
        return instance;
    }

    public AddonConfig getAddonConfig() {
        return addonConfig;
    }

    public DiscordCHATBridge getBridge() {
        return bridge;
    }
}
