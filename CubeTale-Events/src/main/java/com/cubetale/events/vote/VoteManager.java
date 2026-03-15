package com.cubetale.events.vote;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.function.Consumer;

public class VoteManager {

    private final CubeTaleEvents plugin;
    private final Map<UUID, EventType> votes = new HashMap<>();
    private final Map<EventType, Integer> tally = new EnumMap<>(EventType.class);
    private final List<EventType> options = new ArrayList<>();
    private boolean active = false;
    private int taskId = -1;

    public VoteManager(CubeTaleEvents plugin) {
        this.plugin = plugin;
    }

    public void startVote(int durationSeconds, Consumer<EventType> callback) {
        if (active) return;
        active = true;
        votes.clear();
        tally.clear();
        options.clear();

        // Pick 4 random event types as vote options
        List<EventType> all = Arrays.asList(EventType.values());
        Collections.shuffle(all);
        for (int i = 0; i < Math.min(4, all.size()); i++) {
            options.add(all.get(i));
            tally.put(all.get(i), 0);
        }

        // Announce vote
        plugin.msg().broadcastList("vote-started", Map.of("seconds", String.valueOf(durationSeconds)));
        for (int i = 0; i < options.size(); i++) {
            EventType et = options.get(i);
            Bukkit.broadcastMessage(plugin.msg().get("vote-options", Map.of(
                "num", String.valueOf(i + 1),
                "event", et.getDisplayName(),
                "type", et.name()
            )));
        }

        taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            active = false;
            EventType winner = determineWinner();
            announceResults(winner);
            callback.accept(winner);
        }, durationSeconds * 20L);
    }

    public boolean castVote(Player player, String input) {
        if (!active) return false;

        EventType voted = null;
        // Try by name or number
        try {
            int num = Integer.parseInt(input);
            if (num >= 1 && num <= options.size()) voted = options.get(num - 1);
        } catch (NumberFormatException e) {
            voted = EventType.fromString(input);
            if (voted != null && !options.contains(voted)) voted = null;
        }

        if (voted == null) return false;

        boolean alreadyVoted = votes.containsKey(player.getUniqueId());
        if (alreadyVoted) {
            plugin.msg().send(player, "already-voted", Map.of("event",
                votes.get(player.getUniqueId()).getDisplayName()));
            return false;
        }

        votes.put(player.getUniqueId(), voted);
        tally.merge(voted, 1, Integer::sum);
        plugin.msg().send(player, "vote-cast", Map.of("event", voted.getDisplayName()));
        return true;
    }

    private EventType determineWinner() {
        return tally.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(options.isEmpty() ? EventType.DROP_PARTY : options.get(0));
    }

    private void announceResults(EventType winner) {
        int topVotes = tally.getOrDefault(winner, 0);
        plugin.msg().broadcastList("vote-results", Map.of(
            "event", winner.getDisplayName(),
            "votes", String.valueOf(topVotes)
        ));
    }

    public void cancelVote() {
        active = false;
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    public boolean isActive() { return active; }
    public List<EventType> getOptions() { return Collections.unmodifiableList(options); }
    public Map<EventType, Integer> getTally() { return Collections.unmodifiableMap(tally); }
}
