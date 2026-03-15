package com.cubetale.events.command;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventState;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.leaderboard.LeaderboardManager;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Stream;

public class EventCommand implements CommandExecutor, TabCompleter {

    private final CubeTaleEvents plugin;

    public EventCommand(CubeTaleEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help"     -> sendHelp(sender);
            case "join"     -> cmdJoin(sender);
            case "leave"    -> cmdLeave(sender);
            case "spectate" -> cmdSpectate(sender);
            case "info"     -> cmdInfo(sender);
            case "types"    -> cmdTypes(sender);
            case "top"      -> cmdTop(sender);
            case "stats"    -> cmdStats(sender, args);
            case "start"    -> cmdStart(sender, args);
            case "stop"     -> cmdStop(sender);
            case "reload"   -> cmdReload(sender);
            case "setlocation" -> cmdSetLocation(sender, args);
            case "announce" -> cmdAnnounce(sender, args);
            default -> plugin.msg().send(sender, "unknown-command");
        }
        return true;
    }

    // ── Sub-commands ──────────────────────────────────────────────────────────

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(MessageUtil.colorize("&#FFD700&lCubeTale Events — Help"));
        sender.sendMessage(MessageUtil.colorize("  &f/event join &7— Join the current event"));
        sender.sendMessage(MessageUtil.colorize("  &f/event leave &7— Leave the current event"));
        sender.sendMessage(MessageUtil.colorize("  &f/event spectate &7— Spectate the current event"));
        sender.sendMessage(MessageUtil.colorize("  &f/event info &7— Show current event info"));
        sender.sendMessage(MessageUtil.colorize("  &f/event types &7— List all event types"));
        sender.sendMessage(MessageUtil.colorize("  &f/event top &7— Show the leaderboard"));
        sender.sendMessage(MessageUtil.colorize("  &f/event stats [player] &7— Show player stats"));
        sender.sendMessage(MessageUtil.colorize("  &f/eventvote <type> &7— Vote for next event"));
        if (sender.hasPermission("cubetaleevents.admin")) {
            sender.sendMessage(MessageUtil.colorize("&#FF6B35&lAdmin:"));
            sender.sendMessage(MessageUtil.colorize("  &f/event start <type> [joinSeconds] &7— Start event"));
            sender.sendMessage(MessageUtil.colorize("  &f/event stop &7— Stop current event"));
            sender.sendMessage(MessageUtil.colorize("  &f/event setlocation <name> &7— Set a location"));
            sender.sendMessage(MessageUtil.colorize("  &f/event announce <msg> &7— Post to Discord"));
            sender.sendMessage(MessageUtil.colorize("  &f/event reload &7— Reload config"));
        }
    }

    private void cmdJoin(CommandSender sender) {
        if (!(sender instanceof Player player)) { plugin.msg().send(sender, "player-only"); return; }
        if (!player.hasPermission("cubetaleevents.join")) { plugin.msg().send(sender, "no-permission"); return; }

        GameEvent current = plugin.getEventManager().getCurrentEvent();
        if (current == null || current.getState() == EventState.ENDED) {
            plugin.msg().send(sender, "no-event-running"); return;
        }
        if (current.isParticipant(player)) { plugin.msg().send(sender, "already-in-event"); return; }
        if (current.isFull()) {
            plugin.msg().send(sender, "event-full",
                Map.of("max", String.valueOf(current.getMaxParticipants()))); return;
        }
        plugin.getEventManager().playerJoin(player);
    }

    private void cmdLeave(CommandSender sender) {
        if (!(sender instanceof Player player)) { plugin.msg().send(sender, "player-only"); return; }
        if (!plugin.getEventManager().playerLeave(player)) plugin.msg().send(sender, "not-in-event");
    }

    private void cmdSpectate(CommandSender sender) {
        if (!(sender instanceof Player player)) { plugin.msg().send(sender, "player-only"); return; }
        if (!player.hasPermission("cubetaleevents.spectate")) { plugin.msg().send(sender, "no-permission"); return; }
        if (!plugin.getEventManager().playerSpectate(player)) plugin.msg().send(sender, "no-event-running");
    }

    private void cmdInfo(CommandSender sender) {
        GameEvent current = plugin.getEventManager().getCurrentEvent();
        if (current == null) { plugin.msg().send(sender, "no-event-running"); return; }
        sender.sendMessage(MessageUtil.colorize("&#FFD700Current Event: &f" + current.getType().getDisplayName()));
        sender.sendMessage(MessageUtil.colorize("  &7State: &f" + current.getState().name()));
        sender.sendMessage(MessageUtil.colorize("  &7Players: &f" + current.getParticipantCount()));
        sender.sendMessage(MessageUtil.colorize("  &7Description: &f" + current.getDescription()));
    }

    private void cmdTypes(CommandSender sender) {
        sender.sendMessage(MessageUtil.colorize("&#FFD700Available event types:"));
        for (EventType t : EventType.values()) {
            sender.sendMessage(MessageUtil.colorize("  &e" + t.name() + " &7— " + t.getDisplayName()));
        }
    }

    private void cmdTop(CommandSender sender) {
        if (!plugin.getEventsConfig().isLeaderboardEnabled()) {
            sender.sendMessage(MessageUtil.colorize("&cLeaderboard is disabled.")); return;
        }
        List<LeaderboardManager.PlayerStat> top =
                plugin.getLeaderboardManager().getTopWinners(plugin.getEventsConfig().getLeaderboardTopCount());
        sender.sendMessage(plugin.msg().get("leaderboard-header"));
        if (top.isEmpty()) { sender.sendMessage(plugin.msg().get("leaderboard-empty")); return; }
        for (int i = 0; i < top.size(); i++) {
            LeaderboardManager.PlayerStat s = top.get(i);
            sender.sendMessage(plugin.msg().get("leaderboard-entry", Map.of(
                "rank", String.valueOf(i + 1),
                "player", s.name(),
                "wins", String.valueOf(s.wins()),
                "s", s.wins() == 1 ? "" : "s"
            )));
        }
    }

    private void cmdStats(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) { sender.sendMessage(MessageUtil.colorize("&cPlayer not found.")); return; }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage(MessageUtil.colorize("&cUsage: /event stats <player>")); return;
        }
        LeaderboardManager.PlayerStat stat = plugin.getLeaderboardManager().getPlayerStat(target.getUniqueId());
        sender.sendMessage(plugin.msg().get("your-stats", Map.of(
            "wins", String.valueOf(stat.wins()),
            "played", String.valueOf(stat.plays())
        )));
    }

    // ── Admin commands ────────────────────────────────────────────────────────

    private void cmdStart(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubetaleevents.admin")) { plugin.msg().send(sender, "no-permission"); return; }
        if (plugin.getEventManager().hasRunningEvent()) {
            plugin.msg().send(sender, "event-already-running", Map.of(
                "event", plugin.getEventManager().getCurrentEvent().getType().getDisplayName())); return;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.colorize("&cUsage: /event start <type> [joinSeconds]")); return;
        }
        EventType type = EventType.fromString(args[1]);
        if (type == null) { plugin.msg().send(sender, "invalid-event-type", Map.of("type", args[1])); return; }
        int joinSeconds = args.length >= 3 ? parseInt(args[2], 30) : 30;

        boolean started = plugin.getEventManager().startEvent(type, joinSeconds);
        if (!started) sender.sendMessage(MessageUtil.colorize("&cFailed to start event. It may be disabled."));
        else plugin.msg().broadcast("event-force-started", Map.of(
            "event", type.getDisplayName(),
            "admin", sender.getName()
        ));
    }

    private void cmdStop(CommandSender sender) {
        if (!sender.hasPermission("cubetaleevents.admin")) { plugin.msg().send(sender, "no-permission"); return; }
        if (!plugin.getEventManager().hasRunningEvent()) { plugin.msg().send(sender, "no-event-running"); return; }
        plugin.msg().broadcast("event-force-stopped", Map.of("admin", sender.getName()));
        plugin.getEventManager().endCurrentEvent(true);
    }

    private void cmdReload(CommandSender sender) {
        if (!sender.hasPermission("cubetaleevents.admin")) { plugin.msg().send(sender, "no-permission"); return; }
        plugin.reload();
        plugin.msg().reload();
        plugin.msg().send(sender, "plugin-reloaded");
    }

    private void cmdSetLocation(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubetaleevents.admin")) { plugin.msg().send(sender, "no-permission"); return; }
        if (!(sender instanceof Player player)) { plugin.msg().send(sender, "player-only"); return; }
        if (args.length < 2) { sender.sendMessage(MessageUtil.colorize("&cUsage: /event setlocation <name>")); return; }

        String name = args[1].toLowerCase();
        Location loc = player.getLocation();
        String locStr = loc.getWorld().getName() + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();

        // Determine config path based on name
        String configPath = switch (name) {
            case "drop_party" -> "events.drop_party.location";
            case "spleef"     -> "events.spleef.arena-location";
            case "pvp"        -> "events.pvp_tournament.arena-location";
            case "tnt_run"    -> "events.tnt_run.arena-location";
            default -> "events." + name + ".location";
        };

        plugin.getConfig().set(configPath, locStr);
        plugin.saveConfig();
        plugin.msg().send(sender, "location-set", Map.of("name", name));
    }

    private void cmdAnnounce(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubetaleevents.admin")) { plugin.msg().send(sender, "no-permission"); return; }
        if (args.length < 2) { sender.sendMessage(MessageUtil.colorize("&cUsage: /event announce <message>")); return; }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        plugin.getDiscordBridge().postCustomAnnouncement("Event Announcement", message);
        sender.sendMessage(MessageUtil.colorize("&aAnnouncement posted to Discord."));
    }

    // ── Tab completion ────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("help", "join", "leave", "spectate", "info", "types", "top", "stats"));
            if (sender.hasPermission("cubetaleevents.admin"))
                subs.addAll(List.of("start", "stop", "reload", "setlocation", "announce"));
            return filter(subs, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("start")) {
                return filter(Stream.of(EventType.values()).map(EventType::name).toList(), args[1]);
            }
            if (args[0].equalsIgnoreCase("setlocation")) {
                return filter(List.of("drop_party", "spleef", "pvp", "tnt_run"), args[1]);
            }
            if (args[0].equalsIgnoreCase("stats")) {
                return null; // shows online players
            }
        }
        return List.of();
    }

    private List<String> filter(List<String> list, String prefix) {
        return list.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).toList();
    }

    private int parseInt(String s, int fallback) {
        try { return Integer.parseInt(s); } catch (Exception e) { return fallback; }
    }
}
