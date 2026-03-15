package com.cubetale.events.event;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.types.*;
import com.cubetale.events.leaderboard.LeaderboardManager;
import com.cubetale.events.reward.RewardManager;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.*;

public class EventManager {

    private final CubeTaleEvents plugin;
    private GameEvent currentEvent;
    private int countdownTaskId = -1;

    public EventManager(CubeTaleEvents plugin) {
        this.plugin = plugin;
    }

    // ── Start / Stop ──────────────────────────────────────────────────────────

    /**
     * Creates a new event of the given type and opens it for joining.
     * Starts a countdown before the event officially begins.
     *
     * @param type          The event type to create.
     * @param joinSeconds   Seconds players have to join before event starts.
     * @return true if the event was created, false if one is already running.
     */
    public boolean startEvent(EventType type, int joinSeconds) {
        if (currentEvent != null && currentEvent.getState() != EventState.ENDED) {
            return false;
        }

        GameEvent event = createEvent(type);
        if (event == null || !event.isEnabled()) return false;

        currentEvent = event;
        currentEvent.setState(EventState.JOINING);

        // Announce
        Map<String, String> ph = Map.of(
            "event", type.getDisplayName(),
            "countdown", String.valueOf(joinSeconds),
            "description", event.getDescription()
        );
        plugin.msg().broadcastList("event-starting", ph);
        playSound("start");

        // Discord
        if (plugin.getEventsConfig().isDiscordAnnounceStart()) {
            plugin.getDiscordBridge().postEventStart(event);
        }

        // Countdown
        startJoinCountdown(joinSeconds);
        return true;
    }

    private void startJoinCountdown(int seconds) {
        countdownTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int remaining = seconds;

            @Override
            public void run() {
                if (currentEvent == null || currentEvent.getState() == EventState.ENDED) {
                    cancelCountdown();
                    return;
                }

                if (remaining <= 0) {
                    cancelCountdown();
                    launchEvent();
                    return;
                }

                if (remaining <= 5 || remaining == 10 || remaining == 30 || remaining == 60) {
                    Map<String, String> ph = Map.of(
                        "seconds", String.valueOf(remaining),
                        "s", remaining == 1 ? "" : "s",
                        "event", currentEvent.getType().getDisplayName()
                    );
                    plugin.msg().broadcast("countdown-broadcast", ph);
                    broadcastTitle(ph);
                    playSound("countdown");
                }
                remaining--;
            }
        }, 0L, 20L);
    }

    private void broadcastTitle(Map<String, String> ph) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            String title    = plugin.msg().get("countdown-title", ph);
            String subtitle = plugin.msg().get("countdown-subtitle", ph);
            p.sendTitle(title, subtitle, 5, 25, 5);
        }
    }

    private void launchEvent() {
        if (currentEvent == null) return;
        int min = plugin.getEventsConfig().getMinPlayersToStart();
        if (currentEvent.getParticipantCount() < min) {
            plugin.msg().broadcast("too-few-players", Map.of("min", String.valueOf(min)));
            cancelCurrentEvent();
            return;
        }
        currentEvent.setState(EventState.RUNNING);
        currentEvent.start();

        Map<String, String> ph = Map.of(
            "event", currentEvent.getType().getDisplayName(),
            "description", currentEvent.getDescription(),
            "players", String.valueOf(currentEvent.getParticipantCount())
        );
        plugin.msg().broadcastList("event-started", ph);
    }

    public void endCurrentEvent(boolean forced) {
        if (currentEvent == null) return;
        if (forced) {
            plugin.msg().broadcast("event-cancelled",
                Map.of("event", currentEvent.getType().getDisplayName()));
        }
        currentEvent.end(forced);
        distributeRewards();
        currentEvent.setState(EventState.ENDED);

        if (!forced && plugin.getEventsConfig().isDiscordAnnounceEnd()) {
            plugin.getDiscordBridge().postEventEnd(currentEvent);
        }

        currentEvent = null;
    }

    public void forceStopCurrentEvent() {
        if (currentEvent != null) endCurrentEvent(true);
    }

    private void cancelCurrentEvent() {
        cancelCountdown();
        if (currentEvent != null) {
            plugin.msg().broadcast("event-cancelled",
                Map.of("event", currentEvent.getType().getDisplayName()));
            currentEvent.setState(EventState.ENDED);
            currentEvent = null;
        }
    }

    // ── Rewards ───────────────────────────────────────────────────────────────

    private void distributeRewards() {
        if (currentEvent == null) return;
        List<Map.Entry<UUID, Integer>> ranking = currentEvent.getRanking();
        RewardManager rewards = new RewardManager(plugin);

        // Broadcast winner list
        String first  = getPlayerName(ranking, 0);
        String second = getPlayerName(ranking, 1);
        String third  = getPlayerName(ranking, 2);

        if (first == null) {
            plugin.msg().broadcast("no-winner", Map.of());
        } else {
            Map<String, String> ph = Map.of(
                "event", currentEvent.getType().getDisplayName(),
                "1st", first, "2nd", second != null ? second : "-",
                "3rd", third != null ? third : "-"
            );
            plugin.msg().broadcastList("winner-broadcast", ph);

            // Give rewards
            rewards.giveReward(ranking, 0, "first-place");
            rewards.giveReward(ranking, 1, "second-place");
            rewards.giveReward(ranking, 2, "third-place");

            // Record leaderboard wins
            if (plugin.getEventsConfig().isLeaderboardEnabled()) {
                LeaderboardManager lb = plugin.getLeaderboardManager();
                if (ranking.size() > 0) lb.addWin(ranking.get(0).getKey(), currentEvent.getType());
                for (Map.Entry<UUID, Integer> e : ranking) {
                    lb.addPlay(e.getKey(), currentEvent.getType());
                }
            }

            // Discord
            if (plugin.getEventsConfig().isDiscordAnnounceWinner()) {
                plugin.getDiscordBridge().postEventWinner(currentEvent, first, second, third);
            }
        }

        // Participation rewards
        if (plugin.getEventsConfig().isParticipationEnabled()) {
            for (Map.Entry<UUID, Integer> e : ranking) {
                boolean hasScore = !plugin.getEventsConfig().isParticipationRequireScore()
                        || e.getValue() > 0;
                if (hasScore) {
                    Player p = Bukkit.getPlayer(e.getKey());
                    if (p != null) rewards.runCommands(
                            plugin.getEventsConfig().getParticipationCommands(), p.getName());
                }
            }
        }

        // Sound
        playSound("winner");
    }

    private String getPlayerName(List<Map.Entry<UUID, Integer>> ranking, int index) {
        if (index >= ranking.size()) return null;
        Player p = Bukkit.getPlayer(ranking.get(index).getKey());
        return p != null ? p.getName() : Bukkit.getOfflinePlayer(ranking.get(index).getKey()).getName();
    }

    // ── Player join/leave ─────────────────────────────────────────────────────

    public boolean playerJoin(Player player) {
        if (currentEvent == null) return false;
        boolean joined = currentEvent.addParticipant(player);
        if (joined && plugin.getEventsConfig().isEventJoinBroadcast()) {
            plugin.msg().broadcast("player-joined", Map.of(
                "player", player.getName(),
                "count", String.valueOf(currentEvent.getParticipantCount()),
                "max", String.valueOf(currentEvent.getMaxParticipants() == Integer.MAX_VALUE
                    ? "∞" : String.valueOf(currentEvent.getMaxParticipants()))
            ));
            plugin.msg().send(player, "joined-event",
                Map.of("event", currentEvent.getType().getDisplayName()));
            playSound(player, "join");
        }
        return joined;
    }

    public boolean playerLeave(Player player) {
        if (currentEvent == null) return false;
        boolean left = currentEvent.removeParticipant(player);
        if (left && plugin.getEventsConfig().isEventLeaveBroadcast()) {
            plugin.msg().broadcast("player-left", Map.of("player", player.getName()));
            plugin.msg().send(player, "left-event",
                Map.of("event", currentEvent.getType().getDisplayName()));
        }
        return left;
    }

    public boolean playerSpectate(Player player) {
        if (currentEvent == null) return false;
        boolean ok = currentEvent.addSpectator(player);
        if (ok) plugin.msg().send(player, "spectating-event",
            Map.of("event", currentEvent.getType().getDisplayName()));
        return ok;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    private GameEvent createEvent(EventType type) {
        return switch (type) {
            case DROP_PARTY          -> new DropPartyEvent(plugin);
            case TRIVIA              -> new TriviaEvent(plugin);
            case MOB_HUNT            -> new MobHuntEvent(plugin);
            case ORE_HUNT            -> new OreHuntEvent(plugin);
            case FISHING_COMPETITION -> new FishingCompetitionEvent(plugin);
            case SPLEEF              -> new SpleefEvent(plugin);
            case PVP_TOURNAMENT      -> new PvpTournamentEvent(plugin);
            case LUCKY_BLOCK         -> new LuckyBlockEvent(plugin);
            case SCAVENGER_HUNT      -> new ScavengerHuntEvent(plugin);
            case TNT_RUN             -> new TntRunEvent(plugin);
        };
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private void cancelCountdown() {
        if (countdownTaskId != -1) {
            Bukkit.getScheduler().cancelTask(countdownTaskId);
            countdownTaskId = -1;
        }
    }

    private void playSound(String key) {
        String soundName = plugin.getEventsConfig().getSound(key);
        if (soundName == null || soundName.isEmpty()) return;
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase());
            for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {}
    }

    private void playSound(Player player, String key) {
        String soundName = plugin.getEventsConfig().getSound(key);
        if (soundName == null || soundName.isEmpty()) return;
        try {
            player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase()), 1f, 1f);
        } catch (IllegalArgumentException ignored) {}
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public GameEvent getCurrentEvent() { return currentEvent; }
    public boolean hasRunningEvent() { return currentEvent != null && currentEvent.getState() != EventState.ENDED; }
}
