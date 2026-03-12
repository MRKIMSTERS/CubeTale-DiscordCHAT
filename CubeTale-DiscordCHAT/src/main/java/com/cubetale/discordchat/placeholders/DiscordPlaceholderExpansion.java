package com.cubetale.discordchat.placeholders;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DiscordPlaceholderExpansion extends PlaceholderExpansion {

    private final CubeTaleDiscordChat plugin;

    public DiscordPlaceholderExpansion(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "cubetaledc";
    }

    @Override
    public @NotNull String getAuthor() {
        return "CubeTale";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        switch (params.toLowerCase()) {
            case "linked":
                return plugin.getDatabaseManager().isLinked(player.getUniqueId()) ? "true" : "false";

            case "discord_tag":
                String tag = plugin.getDatabaseManager().getDiscordTag(player.getUniqueId());
                return tag != null ? tag : "Not Linked";

            case "discord_id":
                String id = plugin.getDatabaseManager().getDiscordId(player.getUniqueId());
                return id != null ? id : "Not Linked";

            case "bot_status":
                return plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()
                        ? "Online" : "Offline";

            case "bot_ping":
                if (plugin.getDiscordBot() != null && plugin.getDiscordBot().isConnected()
                        && plugin.getDiscordBot().getJda() != null) {
                    return plugin.getDiscordBot().getJda().getGatewayPing() + "ms";
                }
                return "N/A";

            case "guild_name":
                if (plugin.getDiscordBot() != null && plugin.getDiscordBot().getGuild() != null) {
                    return plugin.getDiscordBot().getGuild().getName();
                }
                return "N/A";

            default:
                return null;
        }
    }
}
