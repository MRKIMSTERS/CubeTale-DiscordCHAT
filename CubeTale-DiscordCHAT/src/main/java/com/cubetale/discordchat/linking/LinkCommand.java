package com.cubetale.discordchat.linking;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class LinkCommand {

    private final CubeTaleDiscordChat plugin;
    private final LinkManager linkManager;

    public LinkCommand(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        this.linkManager = plugin.getLinkManager();
    }

    public boolean handleLink(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessagesConfig().get("errors.player-only"));
            return true;
        }

        if (!plugin.getConfigManager().isLinkingEnabled()) {
            sender.sendMessage("§cAccount linking is disabled on this server.");
            return true;
        }

        Player player = (Player) sender;

        // If a code is provided, they are entering from Discord -> Minecraft flow
        // (not typical, but supported)
        if (args.length > 0) {
            sender.sendMessage("§cUse the Discord /link <code> command to link your account.");
            return true;
        }

        // Check if already linked
        if (plugin.getDatabaseManager().isLinked(player.getUniqueId())) {
            String discordTag = plugin.getDatabaseManager().getDiscordTag(player.getUniqueId());
            sender.sendMessage(plugin.getMessagesConfig().get("link.already-linked",
                    "discord", discordTag != null ? discordTag : "Unknown"));
            return true;
        }

        // Generate code
        String code = linkManager.generateCode(player);
        int expirySeconds = plugin.getConfigManager().getCodeExpiry();

        sender.sendMessage(plugin.getMessagesConfig().get("link.prompt",
                "code", code,
                "time", String.valueOf(expirySeconds)));

        plugin.getPluginLogger().debug("Link code for " + player.getName() + ": " + code);
        return true;
    }

    public boolean handleUnlink(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessagesConfig().get("errors.player-only"));
            return true;
        }

        Player player = (Player) sender;

        if (!plugin.getDatabaseManager().isLinked(player.getUniqueId())) {
            sender.sendMessage(plugin.getMessagesConfig().get("unlink.not-linked"));
            return true;
        }

        boolean success = linkManager.unlinkPlayer(player.getUniqueId());
        if (success) {
            sender.sendMessage(plugin.getMessagesConfig().get("unlink.success"));
        } else {
            sender.sendMessage("§cFailed to unlink account. Please try again.");
        }
        return true;
    }

    public boolean handleLinked(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Check own status
            if (!(sender instanceof Player)) {
                sender.sendMessage(plugin.getMessagesConfig().get("errors.player-only"));
                return true;
            }
            Player player = (Player) sender;
            if (plugin.getDatabaseManager().isLinked(player.getUniqueId())) {
                String discordTag = plugin.getDatabaseManager().getDiscordTag(player.getUniqueId());
                sender.sendMessage(plugin.getMessagesConfig().get("linked.self",
                        "discord", discordTag != null ? discordTag : "Unknown"));
            } else {
                sender.sendMessage(plugin.getMessagesConfig().get("linked.self-not-linked"));
            }
        } else {
            // Check another player
            if (!sender.hasPermission("cubetale.admin")) {
                sender.sendMessage(plugin.getMessagesConfig().get("errors.no-permission"));
                return true;
            }

            String targetName = args[0];
            Player target = Bukkit.getPlayer(targetName);
            UUID targetUUID;

            if (target != null) {
                targetUUID = target.getUniqueId();
                targetName = target.getName();
            } else {
                @SuppressWarnings("deprecation")
                org.bukkit.OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetName);
                if (!offlinePlayer.hasPlayedBefore()) {
                    sender.sendMessage(plugin.getMessagesConfig().get("errors.player-not-found",
                            "player", targetName));
                    return true;
                }
                targetUUID = offlinePlayer.getUniqueId();
                targetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : targetName;
            }

            if (plugin.getDatabaseManager().isLinked(targetUUID)) {
                String discordTag = plugin.getDatabaseManager().getDiscordTag(targetUUID);
                sender.sendMessage(plugin.getMessagesConfig().get("linked.other",
                        "player", targetName,
                        "discord", discordTag != null ? discordTag : "Unknown"));
            } else {
                sender.sendMessage(plugin.getMessagesConfig().get("linked.other-not-linked",
                        "player", targetName));
            }
        }
        return true;
    }
}
