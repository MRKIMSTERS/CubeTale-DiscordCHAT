package com.cubetale.discordchat.discord;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.ColorConverter;
import com.cubetale.discordchat.util.MessageFormatter;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import org.bukkit.Bukkit;
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
            case "execute":
                handleExecute(event);
                break;
            case "link":
                handleLink(event);
                break;
        }
    }

    private void handlePlayers(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        Collection<? extends Player> onlinePlayers = Bukkit.getOnlinePlayers();
        int online = onlinePlayers.size();
        int max = Bukkit.getMaxPlayers();

        StringBuilder playerList = new StringBuilder();
        if (onlinePlayers.isEmpty()) {
            playerList.append("*No players online.*");
        } else {
            for (Player p : onlinePlayers) {
                String avatarEmoji = "👤";
                playerList.append(avatarEmoji).append(" **").append(p.getName()).append("**\n");
            }
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("👥 Online Players (" + online + "/" + max + ")")
                .setDescription(playerList.toString())
                .setColor(ColorConverter.hexToInt("#7289DA"))
                .setTimestamp(Instant.now());

        event.getHook().editOriginalEmbeds(embed.build()).queue();
    }

    private void handleStatus(SlashCommandInteractionEvent event) {
        event.deferReply().queue();

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String version = Bukkit.getVersion();
        long uptimeSeconds = (System.currentTimeMillis() - plugin.getDiscordBot().getStartTime()) / 1000;
        String uptime = MessageFormatter.formatUptime(uptimeSeconds);

        // Get TPS (Paper only — use reflection for Spigot compatibility)
        double tps = 20.0;
        try {
            java.lang.reflect.Method getTpsMethod = Bukkit.getServer().getClass().getMethod("getTPS");
            double[] tpsArray = (double[]) getTpsMethod.invoke(Bukkit.getServer());
            if (tpsArray != null && tpsArray.length > 0) {
                tps = Math.min(tpsArray[0], 20.0);
            }
        } catch (Exception e) {
            // Spigot doesn't expose getTPS — keep default 20.0
        }

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

    private void handleExecute(SlashCommandInteractionEvent event) {
        // Check if user has admin role
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

        // Execute on main thread
        final String finalCommand = command;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            try {
                boolean success = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
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

        String discordId = event.getUser().getId();
        String discordTag = event.getUser().getAsTag();

        // Check if code was provided
        if (event.getOption("code") != null) {
            String code = event.getOption("code").getAsString();
            boolean success = plugin.getLinkManager().linkWithCode(code, discordId, discordTag);
            if (success) {
                event.reply("✅ Account linked successfully! Your Minecraft account is now connected.").setEphemeral(true).queue();
            } else {
                event.reply("❌ Invalid or expired code. Use `/link` in Minecraft to get a new code.").setEphemeral(true).queue();
            }
        } else {
            // Just show instructions
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
