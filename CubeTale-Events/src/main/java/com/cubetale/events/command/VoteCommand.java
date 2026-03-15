package com.cubetale.events.command;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.util.MessageUtil;
import com.cubetale.events.vote.VoteManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.stream.Stream;

public class VoteCommand implements CommandExecutor, TabCompleter {

    private final CubeTaleEvents plugin;

    public VoteCommand(CubeTaleEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.msg().send(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("cubetaleevents.vote")) {
            plugin.msg().send(sender, "no-permission");
            return true;
        }

        VoteManager vm = plugin.getEventScheduler().getVoteManager();
        if (vm == null || !vm.isActive()) {
            player.sendMessage(MessageUtil.colorize("&cThere is no active vote right now."));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(MessageUtil.colorize("&7Active vote options:"));
            List<EventType> opts = vm.getOptions();
            for (int i = 0; i < opts.size(); i++) {
                player.sendMessage(MessageUtil.colorize("  &e" + (i + 1) + ". &f" + opts.get(i).getDisplayName()
                        + " &7(" + vm.getTally().getOrDefault(opts.get(i), 0) + " votes)"));
            }
            player.sendMessage(MessageUtil.colorize("  &7Type &f/eventvote <number or type>&7 to vote."));
            return true;
        }

        vm.castVote(player, args[0]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        VoteManager vm = plugin.getEventScheduler().getVoteManager();
        if (args.length == 1 && vm != null && vm.isActive()) {
            return vm.getOptions().stream()
                    .map(EventType::name)
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
