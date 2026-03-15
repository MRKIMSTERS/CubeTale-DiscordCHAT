package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.Map;

public class OreHuntEvent extends GameEvent {

    private Material targetOre;

    public OreHuntEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.ORE_HUNT);
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        var cfg = plugin.getEventsConfig().get();
        String oreName = cfg.getString("events.ore_hunt.target-ore", "DIAMOND_ORE");
        try {
            targetOre = Material.valueOf(oreName.toUpperCase());
        } catch (Exception e) {
            targetOre = Material.DIAMOND_ORE;
        }

        int duration         = getDurationSeconds();
        int broadcastInterval = cfg.getInt("events.ore_hunt.leaderboard-broadcast-interval", 60);

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

    @Override
    public void onPlayerAction(Player player, Object... args) {
        if (!isParticipant(player)) return;
        if (args.length < 1 || !(args[0] instanceof Material broken)) return;
        if (broken != targetOre) return;
        addScore(player, 1);
        Bukkit.broadcastMessage(plugin.msg().get("ore-hunt-mine", Map.of(
            "player", player.getName(),
            "ore", targetOre.name(),
            "count", String.valueOf(getScore(player))
        )));
    }

    private void broadcastLeaderboard() {
        var ranking = getRanking();
        Bukkit.broadcastMessage(MessageUtil.colorize("&#4FC3F7&lOre Hunt Leaderboard:"));
        int rank = 1;
        for (var e : ranking) {
            if (rank > 5) break;
            Player p = Bukkit.getPlayer(e.getKey());
            String name = p != null ? p.getName() : "Unknown";
            Bukkit.broadcastMessage(MessageUtil.colorize("  &b" + rank + ". &f" + name + ": &b" + e.getValue() + " ores"));
            rank++;
        }
    }

    @Override
    public void end(boolean forced) { cancelTask(); }

    public Material getTargetOre() { return targetOre; }
}
