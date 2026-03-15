package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MobHuntEvent extends GameEvent {

    private EntityType targetMob;

    public MobHuntEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.MOB_HUNT);
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        var cfg = plugin.getEventsConfig().get();
        String mobName = cfg.getString("events.mob_hunt.target-mob", "ZOMBIE");
        try {
            targetMob = EntityType.valueOf(mobName.toUpperCase());
        } catch (Exception e) {
            targetMob = EntityType.ZOMBIE;
        }
        Bukkit.broadcastMessage(plugin.msg().get("mob-hunt-kill",
            Map.of("player", "Server", "mob", targetMob.name(), "count", "0"))
            .replace("Server killed", "Hunt target: &f" + targetMob.name()));

        int duration = getDurationSeconds();
        int broadcastInterval = cfg.getInt("events.mob_hunt.leaderboard-broadcast-interval", 60);

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
        if (args.length < 1 || !(args[0] instanceof EntityType killed)) return;
        if (killed != targetMob) return;
        addScore(player, 1);
        Bukkit.broadcastMessage(plugin.msg().get("mob-hunt-kill", Map.of(
            "player", player.getName(),
            "mob", targetMob.name(),
            "count", String.valueOf(getScore(player))
        )));
    }

    private void broadcastLeaderboard() {
        var ranking = getRanking();
        Bukkit.broadcastMessage(MessageUtil.colorize("&#FF6B35&lMob Hunt Leaderboard:"));
        int rank = 1;
        for (var e : ranking) {
            if (rank > 5) break;
            Player p = Bukkit.getPlayer(e.getKey());
            String name = p != null ? p.getName() : "Unknown";
            Bukkit.broadcastMessage(MessageUtil.colorize("  &e" + rank + ". &f" + name + ": &e" + e.getValue() + " kills"));
            rank++;
        }
    }

    @Override
    public void end(boolean forced) { cancelTask(); }

    public EntityType getTargetMob() { return targetMob; }
}
