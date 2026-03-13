package com.cubetale.discordchat;

import com.cubetale.discordchat.config.ConfigManager;
import com.cubetale.discordchat.config.MessagesConfig;
import com.cubetale.discordchat.console.ConsoleManager;
import com.cubetale.discordchat.database.DatabaseManager;
import com.cubetale.discordchat.discord.DiscordBot;
import com.cubetale.discordchat.discord.StatsAutoPost;
import com.cubetale.discordchat.discord.WebhookManager;
import com.cubetale.discordchat.linking.LinkManager;
import com.cubetale.discordchat.minecraft.ChatHandler;
import com.cubetale.discordchat.minecraft.CommandManager;
import com.cubetale.discordchat.minecraft.MinecraftListener;
import com.cubetale.discordchat.placeholders.DiscordPlaceholderExpansion;
import com.cubetale.discordchat.sync.RoleSyncManager;
import com.cubetale.discordchat.util.PluginLogger;
import com.cubetale.discordchat.util.SkinsRestorerHook;
import org.bukkit.plugin.java.JavaPlugin;

public class CubeTaleDiscordChat extends JavaPlugin {

    private static CubeTaleDiscordChat instance;

    private ConfigManager configManager;
    private MessagesConfig messagesConfig;
    private DatabaseManager databaseManager;
    private DiscordBot discordBot;
    private WebhookManager webhookManager;
    private LinkManager linkManager;
    private RoleSyncManager roleSyncManager;
    private ConsoleManager consoleManager;
    private PluginLogger pluginLogger;
    private SkinsRestorerHook skinsRestorerHook;
    private StatsAutoPost statsAutoPost;

    @Override
    public void onEnable() {
        instance = this;

        pluginLogger = new PluginLogger(this);
        pluginLogger.info("Starting CubeTale-DiscordCHAT v" + getDescription().getVersion() + "...");

        // Load configurations
        saveDefaultConfig();
        configManager = new ConfigManager(this);
        messagesConfig = new MessagesConfig(this);

        // Initialize database
        databaseManager = new DatabaseManager(this);
        if (!databaseManager.initialize()) {
            pluginLogger.severe("Failed to initialize database! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize managers
        linkManager = new LinkManager(this);
        webhookManager = new WebhookManager(this);

        // Initialize and start Discord bot
        discordBot = new DiscordBot(this);
        if (!discordBot.start()) {
            pluginLogger.severe("Failed to start Discord bot! Check your bot token in config.yml.");
            pluginLogger.warning("Plugin will remain enabled but Discord features will be unavailable.");
        }

        // Role sync
        roleSyncManager = new RoleSyncManager(this);
        if (getConfig().getBoolean("role-sync.enabled", false)) {
            roleSyncManager.startSyncTask();
        }

        // Console manager
        consoleManager = new ConsoleManager(this);
        if (getConfig().getBoolean("console.enabled", true) && discordBot.isConnected()) {
            consoleManager.initialize();
        }

        // Register Minecraft event listeners
        getServer().getPluginManager().registerEvents(new MinecraftListener(this), this);
        getServer().getPluginManager().registerEvents(new ChatHandler(this), this);

        // Register commands
        CommandManager commandManager = new CommandManager(this);
        if (getCommand("cubetaledc") != null) {
            getCommand("cubetaledc").setExecutor(commandManager);
            getCommand("cubetaledc").setTabCompleter(commandManager);
        }
        if (getCommand("link") != null) {
            getCommand("link").setExecutor(commandManager);
        }
        if (getCommand("unlink") != null) {
            getCommand("unlink").setExecutor(commandManager);
        }
        if (getCommand("linked") != null) {
            getCommand("linked").setExecutor(commandManager);
        }

        // SkinsRestorer hook (soft dependency)
        skinsRestorerHook = new SkinsRestorerHook(this);

        // PlaceholderAPI hook
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DiscordPlaceholderExpansion(this).register();
            pluginLogger.info("PlaceholderAPI hooked successfully.");
        }

        // Stats auto-post
        statsAutoPost = new StatsAutoPost(this);
        if (discordBot.isConnected()) {
            statsAutoPost.start();
        }

        // Send server start notification
        if (discordBot.isConnected()) {
            discordBot.sendServerStartNotification();
        }

        pluginLogger.info("CubeTale-DiscordCHAT has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        pluginLogger.info("Shutting down CubeTale-DiscordCHAT...");

        // Send server stop notification before shutting down
        if (discordBot != null && discordBot.isConnected()) {
            discordBot.sendServerStopNotification();
        }

        if (statsAutoPost != null) {
            statsAutoPost.stop();
        }

        if (consoleManager != null) {
            consoleManager.shutdown();
        }

        if (webhookManager != null) {
            webhookManager.shutdown();
        }

        if (roleSyncManager != null) {
            roleSyncManager.shutdown();
        }

        if (discordBot != null) {
            discordBot.shutdown();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        pluginLogger.info("CubeTale-DiscordCHAT has been disabled.");
        instance = null;
    }

    public void reload() {
        reloadConfig();
        configManager.reload();
        messagesConfig.reload();

        if (webhookManager != null) {
            webhookManager.shutdown();
            webhookManager = new WebhookManager(this);
        }
    }

    // --- Getters ---

    public static CubeTaleDiscordChat getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessagesConfig getMessagesConfig() {
        return messagesConfig;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    public WebhookManager getWebhookManager() {
        return webhookManager;
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public RoleSyncManager getRoleSyncManager() {
        return roleSyncManager;
    }

    public ConsoleManager getConsoleManager() {
        return consoleManager;
    }

    public PluginLogger getPluginLogger() {
        return pluginLogger;
    }

    public SkinsRestorerHook getSkinsRestorerHook() {
        return skinsRestorerHook;
    }
}
