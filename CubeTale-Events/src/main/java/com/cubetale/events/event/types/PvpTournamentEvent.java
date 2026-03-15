package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class PvpTournamentEvent extends GameEvent {

    private final List<UUID> bracket = new ArrayList<>();
    private int matchIndex = 0;
    private UUID currentPlayer1, currentPlayer2;
    private int matchTaskId = -1;

    public PvpTournamentEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.PVP_TOURNAMENT);
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        bracket.addAll(participants);
        Collections.shuffle(bracket);

        Bukkit.broadcastMessage(MessageUtil.colorize("&#FF6B35&lPvP Tournament &fstarted with &e"
                + bracket.size() + " &fplayers!"));

        giveKits();
        startNextMatch();
    }

    private void giveKits() {
        var cfg = plugin.getEventsConfig().get();
        if (!cfg.getBoolean("events.pvp_tournament.kit.enabled", true)) return;
        List<String> items = cfg.getStringList("events.pvp_tournament.kit.items");

        for (UUID uid : bracket) {
            Player p = Bukkit.getPlayer(uid);
            if (p == null) continue;
            p.getInventory().clear();
            for (String item : items) {
                try {
                    String[] parts = item.split(":");
                    Material mat   = Material.valueOf(parts[0].toUpperCase());
                    int amount     = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    p.getInventory().addItem(new ItemStack(mat, amount));
                } catch (Exception ignored) {}
            }
        }
    }

    private void startNextMatch() {
        // Remove players not online
        bracket.removeIf(u -> Bukkit.getPlayer(u) == null);

        if (bracket.size() <= 1) {
            plugin.getEventManager().endCurrentEvent(false);
            return;
        }

        // Pick next pair
        if (matchIndex + 1 >= bracket.size()) matchIndex = 0;
        currentPlayer1 = bracket.get(matchIndex);
        currentPlayer2 = bracket.get(matchIndex + 1);
        matchIndex += 2;

        Player p1 = Bukkit.getPlayer(currentPlayer1);
        Player p2 = Bukkit.getPlayer(currentPlayer2);
        if (p1 == null || p2 == null) {
            startNextMatch();
            return;
        }

        Bukkit.broadcastMessage(MessageUtil.colorize("&#FF6B35[PvP] &fNext match: &e" + p1.getName()
                + " &fvs &e" + p2.getName() + "&f!"));

        // Teleport to arena
        String locStr = plugin.getEventsConfig().get().getString("events.pvp_tournament.arena-location", "");
        Location arena = parseLocation(locStr);
        if (arena != null) {
            p1.teleport(arena.clone().add(5, 0, 0));
            p2.teleport(arena.clone().add(-5, 0, 0));
        }

        // Round time limit
        int roundTime = plugin.getEventsConfig().get().getInt("events.pvp_tournament.round-time", 120);
        if (roundTime > 0) {
            matchTaskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                Bukkit.broadcastMessage(MessageUtil.colorize("&#FF6B35[PvP] &fTime's up! Both players are eliminated."));
                bracket.remove(currentPlayer1);
                bracket.remove(currentPlayer2);
                matchIndex = Math.max(0, matchIndex - 2);
                startNextMatch();
            }, roundTime * 20L);
        }
    }

    /**
     * Called from EventListener on PlayerDeathEvent during the tournament.
     * args[0] = Player killer
     */
    @Override
    public void onPlayerAction(Player player, Object... args) {
        // player = loser (dead)
        if (!isParticipant(player)) return;
        if (matchTaskId != -1) { Bukkit.getScheduler().cancelTask(matchTaskId); matchTaskId = -1; }

        bracket.remove(player.getUniqueId());
        addScore(args.length > 0 && args[0] instanceof Player killer ? killer : player, 1);

        Player killer = args.length > 0 && args[0] instanceof Player k ? k : null;
        if (killer != null) {
            Bukkit.broadcastMessage(MessageUtil.colorize("&#FF6B35[PvP] &e" + killer.getName()
                    + " &feliminated &e" + player.getName() + "&f!"));
            addScore(killer, 1);
        }

        Bukkit.getScheduler().runTaskLater(plugin, this::startNextMatch, 60L);
    }

    @Override
    public void end(boolean forced) {
        cancelTask();
        if (matchTaskId != -1) { Bukkit.getScheduler().cancelTask(matchTaskId); matchTaskId = -1; }
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
