package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.*;

public class TntRunEvent extends GameEvent {

    private final Set<UUID> eliminated = new HashSet<>();
    private final Map<Location, Integer> pendingRemove = new LinkedHashMap<>();
    private Location arenaCenter;
    private double floorY;

    public TntRunEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.TNT_RUN);
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        var cfg = plugin.getEventsConfig().get();
        String locStr    = cfg.getString("events.tnt_run.arena-location", "");
        arenaCenter      = parseLocation(locStr);
        int explodeDelay = cfg.getInt("events.tnt_run.explode-delay", 20);

        if (arenaCenter != null) {
            floorY = arenaCenter.getY();
            for (UUID uid : participants) {
                Player p = Bukkit.getPlayer(uid);
                if (p != null) p.teleport(arenaCenter);
            }
        }

        Bukkit.broadcastMessage(MessageUtil.colorize("&#FF4444&lTNT Run started! Run or fall!"));

        // Every tick: check blocks under players, schedule removal
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            // Process pending block removals
            List<Location> toRemove = new ArrayList<>();
            for (Map.Entry<Location, Integer> e : pendingRemove.entrySet()) {
                int ticks = e.getValue() - 1;
                if (ticks <= 0) {
                    toRemove.add(e.getKey());
                    Block b = e.getKey().getBlock();
                    if (b.getType() != Material.AIR) {
                        b.getWorld().createExplosion(b.getLocation(), 0f, false, false);
                        b.setType(Material.AIR);
                    }
                } else {
                    e.setValue(ticks);
                }
            }
            toRemove.forEach(pendingRemove::remove);

            // Mark blocks under each player
            for (UUID uid : participants) {
                if (eliminated.contains(uid)) continue;
                Player p = Bukkit.getPlayer(uid);
                if (p == null) continue;

                Block under = p.getLocation().subtract(0, 1, 0).getBlock();
                if (under.getType() != Material.AIR && !pendingRemove.containsKey(under.getLocation())) {
                    pendingRemove.put(under.getLocation(), explodeDelay);
                }

                // Check if player fell
                if (arenaCenter != null && p.getLocation().getY() < floorY - 5) {
                    eliminated.add(uid);
                    Bukkit.broadcastMessage(MessageUtil.colorize("&c" + p.getName() + " fell off the arena!"));
                }
            }

            // Check winner
            long alive = participants.stream().filter(u -> !eliminated.contains(u)).count();
            if (alive <= 1) {
                cancelTask();
                plugin.getEventManager().endCurrentEvent(false);
            }
        }, 1L, 1L);
    }

    @Override
    public void end(boolean forced) { cancelTask(); }

    private Location parseLocation(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        } catch (Exception e) { return null; }
    }
}
