package com.cubetale.events.placeholder;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventState;
import com.cubetale.events.event.GameEvent;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class EventsPlaceholderExpansion extends PlaceholderExpansion {

    private final CubeTaleEvents plugin;

    public EventsPlaceholderExpansion(CubeTaleEvents plugin) {
        this.plugin = plugin;
    }

    @Override public @NotNull String getIdentifier() { return "cubetaleevents"; }
    @Override public @NotNull String getAuthor()     { return "MRKIMSTERS"; }
    @Override public @NotNull String getVersion()    { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        GameEvent current = plugin.getEventManager().getCurrentEvent();

        return switch (params.toLowerCase()) {
            case "current_event" -> {
                if (current == null || current.getState() == EventState.ENDED) yield "None";
                yield current.getType().getDisplayName();
            }
            case "current_event_state" -> {
                if (current == null) yield "None";
                yield current.getState().name();
            }
            case "event_players" -> {
                if (current == null) yield "0";
                yield String.valueOf(current.getParticipantCount());
            }
            case "next_event_timer" -> {
                // TODO: expose next event timer from scheduler
                yield "N/A";
            }
            case "wins_total" -> {
                if (player == null) yield "0";
                yield String.valueOf(plugin.getLeaderboardManager().getTotalWins(player.getUniqueId()));
            }
            default -> {
                // wins_{player}
                if (params.startsWith("wins_")) {
                    String name = params.substring(5);
                    var op = plugin.getServer().getOfflinePlayer(name);
                    yield String.valueOf(plugin.getLeaderboardManager().getTotalWins(op.getUniqueId()));
                }
                yield null;
            }
        };
    }
}
