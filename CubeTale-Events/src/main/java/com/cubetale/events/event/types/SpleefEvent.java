package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class SpleefEvent extends GameEvent {

    private final Set<UUID> eliminated = new HashSet<>();
    private Location arenaCenter;

    public SpleefEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.SPLEEF);
    }

    @Override
    public int getMaxParticipants() {
        return plugin.getEventsConfig().get().getInt("events.spleef.max-players", 16);
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        var cfg = plugin.getEventsConfig().get();
        String locStr = cfg.getString("events.spleef.arena-location", "");
        arenaCenter   = parseLocation(locStr);

        Material shovelMat = Material.DIAMOND_SHOVEL;
        try { shovelMat = Material.valueOf(cfg.getString("events.spleef.shovel-material", "DIAMOND_SHOVEL").toUpperCase()); }
        catch (Exception ignored) {}
        final Material shovel = shovelMat;

        for (UUID uid : participants) {
            Player p = Bukkit.getPlayer(uid);
            if (p == null) continue;
            if (arenaCenter != null) p.teleport(arenaCenter);
            p.getInventory().clear();
            p.getInventory().addItem(new ItemStack(shovel));
        }

        Bukkit.broadcastMessage(MessageUtil.colorize("&#FFD700&lSpleef started! Break blocks to make others fall!"));

        // Check for elimination every second
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::checkEliminations, 20L, 20L);
    }

    private void checkEliminations() {
        if (arenaCenter == null) return;
        double floorY = arenaCenter.getY() - 5;

        for (UUID uid : new HashSet<>(participants)) {
            if (eliminated.contains(uid)) continue;
            Player p = Bukkit.getPlayer(uid);
            if (p == null) { eliminated.add(uid); continue; }
            if (p.getLocation().getY() < floorY) {
                eliminated.add(uid);
                p.getInventory().clear();
                p.setGameMode(GameMode.SPECTATOR);
                Bukkit.broadcastMessage(MessageUtil.colorize("&c" + p.getName() + " fell out!"));
            }
        }

        long alive = participants.stream().filter(u -> !eliminated.contains(u)).count();
        if (alive <= 1) {
            cancelTask();
            plugin.getEventManager().endCurrentEvent(false);
        }
    }

    /** args[0] = block Material broken by player */
    @Override
    public void onPlayerAction(Player player, Object... args) {
        if (!isParticipant(player) || eliminated.contains(player.getUniqueId())) return;
        // Score = number of blocks broken
        addScore(player, 1);
    }

    @Override
    public void end(boolean forced) {
        cancelTask();
        // Restore survivors to survival mode
        for (UUID uid : participants) {
            Player p = Bukkit.getPlayer(uid);
            if (p != null && p.getGameMode() == GameMode.SPECTATOR) p.setGameMode(GameMode.SURVIVAL);
        }
    }

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
