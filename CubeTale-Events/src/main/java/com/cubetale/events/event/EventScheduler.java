package com.cubetale.events.event;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.vote.VoteManager;
import org.bukkit.Bukkit;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class EventScheduler {

    private final CubeTaleEvents plugin;
    private int mainTaskId     = -1;
    private int preAnnounceId  = -1;
    private VoteManager voteManager;

    private int rotationIndex = 0;

    public EventScheduler(CubeTaleEvents plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long intervalTicks = (long) plugin.getEventsConfig().getSchedulerIntervalMinutes() * 60 * 20;
        long preAnnounceTicks = (long) plugin.getEventsConfig().getPreAnnounceMinutes() * 60 * 20;

        // Schedule pre-announce first, then the main event
        if (preAnnounceTicks > 0 && preAnnounceTicks < intervalTicks) {
            preAnnounceId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::doPreAnnounce,
                intervalTicks - preAnnounceTicks, intervalTicks);
        }

        mainTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::tryFireEvent,
                intervalTicks, intervalTicks);

        plugin.getLogger().info("Event scheduler started — interval: "
                + plugin.getEventsConfig().getSchedulerIntervalMinutes() + " minutes.");
    }

    public void stop() {
        if (mainTaskId != -1) { Bukkit.getScheduler().cancelTask(mainTaskId); mainTaskId = -1; }
        if (preAnnounceId != -1) { Bukkit.getScheduler().cancelTask(preAnnounceId); preAnnounceId = -1; }
        if (voteManager != null) { voteManager.cancelVote(); voteManager = null; }
    }

    private void doPreAnnounce() {
        if (isBlackoutHour()) return;
        if (Bukkit.getOnlinePlayers().size() < plugin.getEventsConfig().getSchedulerMinOnlinePlayers()) return;
        if (plugin.getEventManager().hasRunningEvent()) return;

        EventType nextType = getNextEventType();
        String nextName = nextType != null ? nextType.getDisplayName() : "Unknown";

        if (plugin.getEventsConfig().isSchedulerUseVote()) {
            // Start vote
            voteManager = new VoteManager(plugin);
            int duration = plugin.getEventsConfig().getVoteDurationSeconds();
            voteManager.startVote(duration, winner -> {
                // vote result callback — schedule the actual fire a bit later
                Bukkit.getScheduler().runTaskLater(plugin, () -> fireEvent(winner), 20L);
            });
        } else {
            // Just announce
            int minutes = plugin.getEventsConfig().getPreAnnounceMinutes();
            plugin.msg().broadcast("auto-event-warning", Map.of(
                "event", nextName,
                "time", minutes + " minute" + (minutes == 1 ? "" : "s")
            ));
        }
    }

    private void tryFireEvent() {
        if (isBlackoutHour()) return;
        if (Bukkit.getOnlinePlayers().size() < plugin.getEventsConfig().getSchedulerMinOnlinePlayers()) {
            plugin.msg().broadcast("no-auto-event", Map.of());
            return;
        }
        if (plugin.getEventManager().hasRunningEvent()) return;

        // If vote already determined a winner, fireEvent was called by the callback.
        // Otherwise fire the rotation event here.
        if (!plugin.getEventsConfig().isSchedulerUseVote()) {
            EventType type = getNextRotationEvent();
            fireEvent(type);
        }
    }

    private void fireEvent(EventType type) {
        if (type == null) return;
        plugin.msg().broadcast("auto-event-start", Map.of("event", type.getDisplayName()));
        // 30-second join window for auto events
        plugin.getEventManager().startEvent(type, 30);
    }

    private EventType getNextEventType() {
        if (plugin.getEventsConfig().isSchedulerUseVote()) return null;
        return getNextRotationEvent();
    }

    private EventType getNextRotationEvent() {
        List<String> rotation = plugin.getEventsConfig().getSchedulerRotation();
        if (rotation.isEmpty()) return EventType.DROP_PARTY;
        String key = rotation.get(rotationIndex % rotation.size());
        rotationIndex++;
        EventType type = EventType.fromString(key);
        return type != null ? type : EventType.DROP_PARTY;
    }

    private boolean isBlackoutHour() {
        List<String> blackouts = plugin.getEventsConfig().getBlackoutHours();
        if (blackouts.isEmpty()) return false;
        LocalTime now = LocalTime.now();
        for (String range : blackouts) {
            String[] parts = range.split("-");
            if (parts.length != 2) continue;
            try {
                LocalTime start = LocalTime.parse(parts[0].trim());
                LocalTime end   = LocalTime.parse(parts[1].trim());
                if (start.isBefore(end)) {
                    if (!now.isBefore(start) && now.isBefore(end)) return true;
                } else {
                    // Crosses midnight
                    if (!now.isBefore(start) || now.isBefore(end)) return true;
                }
            } catch (Exception ignored) {}
        }
        return false;
    }

    public VoteManager getVoteManager() { return voteManager; }
    public void setVoteManager(VoteManager vm) { this.voteManager = vm; }
}
