package com.cubetale.discordchat.minecraft;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.linking.LinkCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final CubeTaleDiscordChat plugin;
    private final LinkCommand linkCommand;

    public CommandManager(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        this.linkCommand = new LinkCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        switch (command.getName().toLowerCase()) {
            case "cubetaledc":
                return handleAdminCommand(sender, args);
            case "link":
                return linkCommand.handleLink(sender, args);
            case "unlink":
                return linkCommand.handleUnlink(sender, args);
            case "linked":
                return linkCommand.handleLinked(sender, args);
            default:
                return false;
        }
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("cubetale.admin")) {
            sender.sendMessage(plugin.getMessagesConfig().get("errors.no-permission"));
            return true;
        }

        if (args.length == 0) {
            sendAdminHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                plugin.reload();
                sender.sendMessage(plugin.getMessagesConfig().get("admin.reload-success"));
                break;

            case "status":
                boolean connected = plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected();
                String status = connected ? "Connected" : "Disconnected";
                String guild = connected && plugin.getDiscordBot().getGuild() != null
                        ? plugin.getDiscordBot().getGuild().getName() : "N/A";
                long ping = connected && plugin.getDiscordBot().getJda() != null
                        ? plugin.getDiscordBot().getJda().getGatewayPing() : -1;

                sender.sendMessage(plugin.getMessagesConfig().get("admin.status",
                        "status", status,
                        "guild", guild,
                        "ping", String.valueOf(ping)));
                break;

            case "debug":
                boolean currentDebug = plugin.getConfig().getBoolean("debug", false);
                plugin.getConfig().set("debug", !currentDebug);
                plugin.saveConfig();
                if (!currentDebug) {
                    sender.sendMessage(plugin.getMessagesConfig().get("admin.debug-enabled"));
                } else {
                    sender.sendMessage(plugin.getMessagesConfig().get("admin.debug-disabled"));
                }
                break;

            default:
                sendAdminHelp(sender);
                break;
        }
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§8[§9CubeTale§8-§bDiscord§8] §7Admin Commands:");
        sender.sendMessage("§7/ctd reload §8- §fReload configuration");
        sender.sendMessage("§7/ctd status §8- §fShow bot status");
        sender.sendMessage("§7/ctd debug §8- §fToggle debug mode");
        sender.sendMessage("§7/link [code] §8- §fLink your Discord account");
        sender.sendMessage("§7/unlink §8- §fUnlink your Discord account");
        sender.sendMessage("§7/linked [player] §8- §fCheck link status");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();

        if (command.getName().equalsIgnoreCase("cubetaledc")) {
            if (args.length == 1) {
                List<String> subCommands = Arrays.asList("reload", "status", "debug");
                for (String sub : subCommands) {
                    if (sub.startsWith(args[0].toLowerCase())) {
                        completions.add(sub);
                    }
                }
            }
        }

        return completions;
    }
}
