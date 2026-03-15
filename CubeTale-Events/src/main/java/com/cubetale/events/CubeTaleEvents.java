package com.cubetale.events;

import com.cubetale.events.command.EventCommand;
import com.cubetale.events.command.VoteCommand;
import com.cubetale.events.config.EventsConfig;
import com.cubetale.events.discord.DiscordBridge;
import com.cubetale.events.event.EventManager;
import com.cubetale.events.event.EventScheduler;
import com.cubetale.events.leaderboard.LeaderboardManager;
import com.cubetale.events.listener.EventListener;
import com.cubetale.events.placeholder.EventsPlaceholderExpansion;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.plugin.java.JavaPlugin;

public class CubeTaleEvents extends JavaPlugin {

    private static CubeTaleEvents instance;

    private EventsConfig eventsConfig;
    private EventManager eventManager;
    private EventScheduler eventScheduler;
    private LeaderboardManager leaderboardManager;
    private DiscordBridge discordBridge;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        saveResource("messages.yml", false);
        saveResource("trivia-questions.yml", false);

        eventsConfig = new EventsConfig(this);

        leaderboardManager = new LeaderboardManager(this);
        leaderboardManager.initialize();

        discordBridge = new DiscordBridge(this);

        eventManager = new EventManager(this);

        getServer().getPluginManager().registerEvents(new EventListener(this), this);

        EventCommand eventCmd = new EventCommand(this);
        if (getCommand("event") != null) {
            getCommand("event").setExecutor(eventCmd);
            getCommand("event").setTabCompleter(eventCmd);
        }

        VoteCommand voteCmd = new VoteCommand(this);
        if (getCommand("eventvote") != null) {
            getCommand("eventvote").setExecutor(voteCmd);
            getCommand("eventvote").setTabCompleter(voteCmd);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new EventsPlaceholderExpansion(this).register();
            getLogger().info("PlaceholderAPI hooked successfully.");
        }

        eventScheduler = new EventScheduler(this);
        if (eventsConfig.isSchedulerEnabled()) {
            eventScheduler.start();
        }

        getLogger().info("CubeTale-Events v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (eventScheduler != null) eventScheduler.stop();
        if (eventManager != null) eventManager.forceStopCurrentEvent();
        if (leaderboardManager != null) leaderboardManager.shutdown();
        getLogger().info("CubeTale-Events disabled.");
    }

    public void reload() {
        reloadConfig();
        eventsConfig.reload();
        if (eventScheduler != null) {
            eventScheduler.stop();
            if (eventsConfig.isSchedulerEnabled()) eventScheduler.start();
        }
    }

    public static CubeTaleEvents getInstance() { return instance; }
    public EventsConfig getEventsConfig() { return eventsConfig; }
    public EventManager getEventManager() { return eventManager; }
    public EventScheduler getEventScheduler() { return eventScheduler; }
    public LeaderboardManager getLeaderboardManager() { return leaderboardManager; }
    public DiscordBridge getDiscordBridge() { return discordBridge; }
    public MessageUtil msg() { return MessageUtil.getInstance(this); }
}
