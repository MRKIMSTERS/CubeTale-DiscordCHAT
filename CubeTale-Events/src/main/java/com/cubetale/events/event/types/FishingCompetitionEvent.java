package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;

public class FishingCompetitionEvent extends GameEvent {

    public FishingCompetitionEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.FISHING_COMPETITION);
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        var cfg = plugin.getEventsConfig().get();
        int duration          = getDurationSeconds();
        int broadcastInterval = cfg.getInt("events.fishing_competition.leaderboard-broadcast-interval", 60);

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            int elapsed = 0;
            @Override
            public void run() {
                elapsed += broadcastInterval;
                broadcastLeaderboard();
                if (elapsed >= duration) {
                    cancelTask();
                    plugin.getEventManager().endCurrentEvent(false);
                }
            }
        }, broadcastInterval * 20L, broadcastInterval * 20L);
    }

    /** args[0] = ExperienceOrb caught (fish catch event) */
    @Override
    public void onPlayerAction(Player player, Object... args) {
        if (!isParticipant(player)) return;
        addScore(player, 1);
        Bukkit.broadcastMessage(plugin.msg().get("fishing-catch", Map.of(
            "player", player.getName(),
            "count", String.valueOf(getScore(player))
        )));
    }

    private void broadcastLeaderboard() {
        var ranking = getRanking();
        Bukkit.broadcastMessage(MessageUtil.colorize("&#1565C0&lFishing Competition Leaderboard:"));
        int rank = 1;
        for (var e : ranking) {
            if (rank > 5) break;
            Player p = Bukkit.getPlayer(e.getKey());
            String name = p != null ? p.getName() : "Unknown";
            Bukkit.broadcastMessage(MessageUtil.colorize("  &9" + rank + ". &f" + name + ": &9" + e.getValue() + " catches"));
            rank++;
        }
    }

    @Override
    public void end(boolean forced) { cancelTask(); }
}
