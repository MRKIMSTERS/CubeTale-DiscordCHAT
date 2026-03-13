package com.cubetale.discordchat.discord;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.ColorConverter;
import com.cubetale.discordchat.util.MessageFormatter;
import com.cubetale.discordchat.util.MinecraftImageRenderer;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SlashCommandManager extends ListenerAdapter {

    private final CubeTaleDiscordChat plugin;
    private final JDA jda;

    public SlashCommandManager(CubeTaleDiscordChat plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;
    }

    public void registerCommands() {
        Guild guild = plugin.getDiscordBot().getGuild();
        if (guild == null) {
            plugin.getPluginLogger().warning("Guild not found, cannot register slash commands.");
            return;
        }

        List<SlashCommandData> commands = new ArrayList<>();

        commands.add(Commands.slash("players", "List online players on the server"));

        commands.add(Commands.slash("status", "Check the server status"));

        commands.add(Commands.slash("profile", "View a Minecraft player's profile card")
                .addOption(OptionType.STRING, "player", "Player name (defaults to your linked account)", false));

        commands.add(Commands.slash("dm", "Send a private message to an online Minecraft player")
                .addOption(OptionType.STRING, "player", "Minecraft player name", true)
                .addOption(OptionType.STRING, "message", "Your message", true));

        commands.add(Commands.slash("execute", "Execute a server console command (admin only)")
                .addOption(OptionType.STRING, "command", "The command to execute", true));

        commands.add(Commands.slash("link", "Start the account linking process")
                .addOption(OptionType.STRING, "code", "Your Minecraft verification code", false));

        guild.updateCommands().addCommands(commands).queue(
                success -> plugin.getPluginLogger().info("Registered " + commands.size() + " Discord slash commands."),
                failure -> plugin.getPluginLogger().warning("Failed to register slash commands: " + failure.getMessage())
        );
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "players":
                handlePlayers(event);
                break;
            case "status":
                handleStatus(event);
                break;
            case "profile":
                handleProfile(event);
                break;
            case "dm":
                handleDm(event);
                break;
            case "execute":
                handleExecute(event);
                break;
            case "link":
                handleLink(event);
                break;
        }
    }

    /**
     * Renders the online player list as a Minecraft-style image and sends it as
     * an embed attachment so it appears inline inside the Discord message.
     */
    private void handlePlayers(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int online = onlinePlayers.size();
        int max    = Bukkit.getMaxPlayers();

        try {
            byte[] imageBytes = MinecraftImageRenderer.renderPlayerList(onlinePlayers, max);

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(ColorConverter.hexToInt("#7289DA"))
                    .setImage("attachment://players.png")
                    .setTimestamp(Instant.now());

            event.getHook()
                 .sendFiles(FileUpload.fromData(imageBytes, "players.png"))
                 .addEmbeds(embed.build())
                 .queue();
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to render player list image: " + e.getMessage());

            // Fallback to plain text if rendering fails
            StringBuilder playerList = new StringBuilder();
            if (onlinePlayers.isEmpty()) {
                playerList.append("*No players online.*");
            } else {
                for (Player p : onlinePlayers) {
                    playerList.append("👤 **").append(p.getName()).append("**\n");
                }
            }
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("👥 Online Players (" + online + "/" + max + ")")
                    .setDescription(playerList.toString())
                    .setColor(ColorConverter.hexToInt("#7289DA"))
                    .setTimestamp(Instant.now());
            event.getHook().editOriginalEmbeds(embed.build()).queue();
        }
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        int online    = Bukkit.getOnlinePlayers().size();
        int max       = Bukkit.getMaxPlayers();
        String version = Bukkit.getVersion();
        long uptimeSeconds = (System.currentTimeMillis() - plugin.getDiscordBot().getStartTime()) / 1000;
        String uptime = MessageFormatter.formatUptime(uptimeSeconds);

        double tps = 20.0;
        try {
            java.lang.reflect.Method getTpsMethod = Bukkit.getServer().getClass().getMethod("getTPS");
            double[] tpsArray = (double[]) getTpsMethod.invoke(Bukkit.getServer());
            if (tpsArray != null && tpsArray.length > 0) {
                tps = Math.min(tpsArray[0], 20.0);
            }
        } catch (Exception ignored) {}

        String tpsColor = tps >= 19.0 ? "🟢" : (tps >= 15.0 ? "🟡" : "🔴");

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("📊 Server Status")
                .setColor(ColorConverter.hexToInt("#7289DA"))
                .addField("🟢 Status", "Online", true)
                .addField("👥 Players", online + "/" + max, true)
                .addField("⏱️ Uptime", uptime, true)
                .addField(tpsColor + " TPS", String.format("%.1f", tps), true)
                .addField("🖥️ Version", version.split("\\(")[0].trim(), true)
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    /**
     * Renders a player profile card image showing head, rank, online status, and key stats.
     * Reads stats on the main thread, then renders + replies asynchronously.
     */
    private void handleProfile(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        String inputName = event.getOption("player") != null
                ? event.getOption("player").getAsString()
                : null;

        // If no name supplied, try to find their linked Minecraft account
        if (inputName == null || inputName.isEmpty()) {
            java.util.UUID linkedUuid = plugin.getDatabaseManager().getMinecraftUUID(event.getUser().getId());
            if (linkedUuid == null) {
                event.getHook().editOriginal(
                        "❌ No player specified and your Discord account is not linked. " +
                        "Use `/link` in Minecraft first, or provide a player name.").queue();
                return;
            }
            OfflinePlayer linked = Bukkit.getOfflinePlayer(linkedUuid);
            inputName = linked.getName() != null ? linked.getName() : linkedUuid.toString();
        }

        final String playerName = inputName;

        // Read stats on main thread (Bukkit stats API is thread-safe for reads, but safer on main)
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
            boolean online        = offline.isOnline();
            String  uuid          = offline.getUniqueId().toString();
            String  rankPrefix    = "";

            int deaths    = 0;
            int kills     = 0;
            int mobKills  = 0;
            long played   = 0L;

            try { deaths   = offline.getStatistic(Statistic.DEATHS);         } catch (Exception ignored) {}
            try { kills    = offline.getStatistic(Statistic.PLAYER_KILLS);    } catch (Exception ignored) {}
            try { mobKills = offline.getStatistic(Statistic.MOB_KILLS);       } catch (Exception ignored) {}
            try { played   = offline.getStatistic(Statistic.PLAY_ONE_MINUTE); } catch (Exception ignored) {}

            if (online && offline.getPlayer() != null) {
                rankPrefix = MinecraftImageRenderer.resolvePrefix(offline.getPlayer());
            } else {
                rankPrefix = MinecraftImageRenderer.resolveOfflinePrefix(offline);
            }

            final String  fRank    = rankPrefix;
            final int     fDeaths  = deaths;
            final int     fKills   = kills;
            final int     fMobs    = mobKills;
            final long    fPlayed  = played;

            // Render + reply off main thread
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    byte[] imageBytes = MinecraftImageRenderer.renderProfileCard(
                            playerName, uuid, fRank, online, fDeaths, fKills, fMobs, fPlayed);

                    EmbedBuilder embed = new EmbedBuilder()
                            .setColor(online ? 0x55FF55 : 0xFF5555)
                            .setImage("attachment://profile.png")
                            .setTimestamp(Instant.now());

                    event.getHook()
                         .sendFiles(FileUpload.fromData(imageBytes, "profile.png"))
                         .addEmbeds(embed.build())
                         .queue();
                } catch (Exception e) {
                    plugin.getPluginLogger().warning("Failed to render profile card: " + e.getMessage());
                    event.getHook().editOriginal("❌ Could not generate profile card for **" + playerName + "**.").queue();
                }
            });
        });
    }

    /**
     * Sends a private message from a Discord user to an online Minecraft player.
     * The message appears in-game with a [Discord DM] prefix.
     */
    private void handleDm(SlashCommandInteractionEvent event) {
        if (!plugin.getConfigManager().isDiscordDmEnabled()) {
            event.reply("❌ Discord → Minecraft DMs are disabled on this server.").setEphemeral(true).queue();
            return;
        }

        String targetName = event.getOption("player") != null ? event.getOption("player").getAsString() : "";
        String dmMessage  = event.getOption("message") != null ? event.getOption("message").getAsString() : "";

        if (targetName.isEmpty() || dmMessage.isEmpty()) {
            event.reply("❌ Please provide a player name and a message.").setEphemeral(true).queue();
            return;
        }

        // Sanitise the message (strip any Minecraft colour codes from Discord input)
        dmMessage = dmMessage.replaceAll("[§&][0-9a-fk-orA-FK-OR]", "").trim();
        if (dmMessage.isEmpty()) {
            event.reply("❌ Message cannot be empty.").setEphemeral(true).queue();
            return;
        }

        final String finalTarget  = targetName;
        final String finalMessage = dmMessage;
        final String senderTag    = event.getUser().getAsTag();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player target = Bukkit.getPlayerExact(finalTarget);
            if (target == null) {
                event.reply("❌ **" + finalTarget + "** is not online right now.").setEphemeral(true).queue();
                return;
            }

            // Format in Minecraft: [Discord DM] SenderTag: message
            String formatted = "§d[Discord DM] §7" + senderTag + "§8: §f" + finalMessage;
            target.sendMessage(formatted);

            // Confirm to the Discord user (ephemeral)
            event.reply("✅ Message delivered to **" + target.getName() + "**.").setEphemeral(true).queue();

            plugin.getPluginLogger().info("[Discord DM] " + senderTag + " → " + target.getName() + ": " + finalMessage);
        });
    }

    private void handleExecute(SlashCommandInteractionEvent event) {
        String adminRoleId = plugin.getConfigManager().getAdminRoleId();
        if (adminRoleId != null && !adminRoleId.isEmpty() && !adminRoleId.equals("ADMIN_ROLE_ID")) {
            boolean hasRole = event.getMember() != null &&
                    event.getMember().getRoles().stream().anyMatch(r -> r.getId().equals(adminRoleId));
            if (!hasRole) {
                event.reply("❌ You do not have permission to execute commands.").setEphemeral(true).queue();
                return;
            }
        }

        if (!plugin.getConfigManager().isConsoleEnabled()) {
            event.reply("❌ Console access is disabled.").setEphemeral(true).queue();
            return;
        }

        String command = event.getOption("command") != null ? event.getOption("command").getAsString() : "";
        if (command.isEmpty()) {
            event.reply("❌ Please provide a command.").setEphemeral(true).queue();
            return;
        }

        final String finalCommand = command;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                plugin.getPluginLogger().info("[Discord Execute] " + event.getUser().getAsTag() + ": " + finalCommand);
            } catch (Exception e) {
                plugin.getPluginLogger().warning("Error executing Discord command: " + e.getMessage());
            }
        });

        event.reply("✅ Command `" + command + "` has been sent to the server console.").setEphemeral(true).queue();
    }

    private void handleLink(SlashCommandInteractionEvent event) {
        if (!plugin.getConfigManager().isLinkingEnabled()) {
            event.reply("❌ Account linking is disabled on this server.").setEphemeral(true).queue();
            return;
        }

        String discordId  = event.getUser().getId();
        String discordTag = event.getUser().getAsTag();

        if (event.getOption("code") != null) {
            String code = event.getOption("code").getAsString();
            boolean success = plugin.getLinkManager().linkWithCode(code, discordId, discordTag);
            if (success) {
                event.reply("✅ Account linked successfully! Your Minecraft account is now connected.").setEphemeral(true).queue();
            } else {
                event.reply("❌ Invalid or expired code. Use `/link` in Minecraft to get a new code.").setEphemeral(true).queue();
            }
        } else {
            if (plugin.getDatabaseManager().isDiscordLinked(discordId)) {
                event.reply("ℹ️ Your Discord account is already linked to a Minecraft account.").setEphemeral(true).queue();
            } else {
                event.reply("ℹ️ To link your account:\n" +
                        "1. Join the Minecraft server\n" +
                        "2. Run `/link` in Minecraft\n" +
                        "3. Use `/link <code>` here with your code").setEphemeral(true).queue();
            }
        }
    }
}
