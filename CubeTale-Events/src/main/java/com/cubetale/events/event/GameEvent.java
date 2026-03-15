package com.cubetale.events.event;

import com.cubetale.events.CubeTaleEvents;
import org.bukkit.entity.Player;

import java.util.*;

/**
 * Abstract base class for all CubeTale event types.
 * Subclasses implement the actual game logic.
 */
public abstract class GameEvent {

    protected final CubeTaleEvents plugin;
    protected final EventType type;
    protected EventState state = EventState.WAITING;

    protected final Set<UUID> participants = new LinkedHashSet<>();
    protected final Set<UUID> spectators  = new LinkedHashSet<>();

    /** Per-player score/count tracked during the event. */
    protected final Map<UUID, Integer> scores = new LinkedHashMap<>();

    protected long startedAt;
    protected int taskId = -1;

    public GameEvent(CubeTaleEvents plugin, EventType type) {
        this.plugin = plugin;
        this.type   = type;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Called when the event officially starts (after join phase). */
    public abstract void start();

    /** Called to end the event (naturally or forced). */
    public abstract void end(boolean forced);

    /** Called by the EventListener to handle game-specific logic for a player action. */
    public void onPlayerAction(Player player, Object... args) {}

    // ── Player management ─────────────────────────────────────────────────────

    public boolean addParticipant(Player player) {
        if (state != EventState.JOINING && state != EventState.RUNNING) return false;
        if (participants.contains(player.getUniqueId())) return false;
        if (isFull()) return false;
        participants.add(player.getUniqueId());
        scores.put(player.getUniqueId(), 0);
        return true;
    }

    public boolean removeParticipant(Player player) {
        boolean removed = participants.remove(player.getUniqueId());
        spectators.remove(player.getUniqueId());
        scores.remove(player.getUniqueId());
        return removed;
    }

    public boolean addSpectator(Player player) {
        if (state != EventState.RUNNING) return false;
        spectators.add(player.getUniqueId());
        return true;
    }

    public boolean isParticipant(Player player) {
        return participants.contains(player.getUniqueId());
    }

    public boolean isSpectator(Player player) {
        return spectators.contains(player.getUniqueId());
    }

    public int getParticipantCount() { return participants.size(); }
    public Set<UUID> getParticipants() { return Collections.unmodifiableSet(participants); }

    // ── Scores ────────────────────────────────────────────────────────────────

    public void addScore(Player player, int amount) {
        scores.merge(player.getUniqueId(), amount, Integer::sum);
    }

    public int getScore(Player player) {
        return scores.getOrDefault(player.getUniqueId(), 0);
    }

    /**
     * Returns the ordered ranking: highest score first.
     * Each entry is [uuid, score].
     */
    public List<Map.Entry<UUID, Integer>> getRanking() {
        List<Map.Entry<UUID, Integer>> list = new ArrayList<>(scores.entrySet());
        list.sort((a, b) -> b.getValue().compareTo(a.getValue()));
        return list;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public EventType getType()   { return type; }
    public EventState getState() { return state; }
    public void setState(EventState state) { this.state = state; }
    public long getStartedAt()   { return startedAt; }

    /** Max participants — override in subclass to enforce a limit. */
    public int getMaxParticipants() { return Integer.MAX_VALUE; }
    public boolean isFull() { return getParticipantCount() >= getMaxParticipants(); }

    public String getDescription() {
        return plugin.getEventsConfig().get()
                .getString("events." + type.getConfigKey() + ".description", "");
    }

    public int getDurationSeconds() {
        return plugin.getEventsConfig().get()
                .getInt("events." + type.getConfigKey() + ".duration-seconds", 120);
    }

    public boolean isEnabled() {
        return plugin.getEventsConfig().get()
                .getBoolean("events." + type.getConfigKey() + ".enabled", true);
    }

    protected void cancelTask() {
        if (taskId != -1) {
            plugin.getServer().getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }
}
