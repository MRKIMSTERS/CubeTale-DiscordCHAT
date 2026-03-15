package com.cubetale.events.reward;

import com.cubetale.events.CubeTaleEvents;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RewardManager {

    private final CubeTaleEvents plugin;

    public RewardManager(CubeTaleEvents plugin) {
        this.plugin = plugin;
    }

    public void giveReward(List<Map.Entry<UUID, Integer>> ranking, int rank, String tier) {
        if (rank >= ranking.size()) return;
        UUID uid = ranking.get(rank).getKey();
        Player player = Bukkit.getPlayer(uid);
        String playerName = player != null ? player.getName()
                : Bukkit.getOfflinePlayer(uid).getName();
        if (playerName == null) playerName = "Unknown";

        List<String> commands;
        if ("first-place".equals(tier))  commands = plugin.getEventsConfig().getFirstPlaceCommands();
        else if ("second-place".equals(tier)) commands = plugin.getEventsConfig().getSecondPlaceCommands();
        else if ("third-place".equals(tier))  commands = plugin.getEventsConfig().getThirdPlaceCommands();
        else return;

        runCommands(commands, playerName);

        // Broadcast
        String broadcast = "";
        if ("first-place".equals(tier)) broadcast = plugin.getEventsConfig().getFirstPlaceBroadcast();
        if (!broadcast.isEmpty()) {
            Bukkit.broadcastMessage(broadcast.replace("{player}", playerName));
        }
    }

    public void runCommands(List<String> commands, String playerName) {
        for (String cmd : commands) {
            String parsed = cmd.replace("{player}", playerName);
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            } catch (Exception e) {
                plugin.getLogger().warning("[Events] Failed to run reward command: " + parsed);
            }
        }
    }
}
