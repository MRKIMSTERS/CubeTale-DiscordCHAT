package com.cubetale.discordchat.discord;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.ColorConverter;
import com.cubetale.discordchat.util.MessageFormatter;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.ChatColor;

import java.util.List;

public class DiscordListener extends ListenerAdapter {

    private final CubeTaleDiscordChat plugin;

    public DiscordListener(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        // Ignore bot messages (including our own webhook messages)
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;

        // Only process messages from the configured chat channel
        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;
        if (!event.getChannel().getId().equals(chatChannelId)) return;

        // Check if discord -> minecraft is enabled
        if (!plugin.getConfigManager().isChatSyncEnabled()) return;
        if (!plugin.getConfigManager().isDiscordToMinecraftEnabled()) return;

        String discordMessage = event.getMessage().getContentDisplay();
        if (discordMessage.isEmpty()) return;

        Member member = event.getMember();
        String displayName = member != null ? member.getEffectiveName() : event.getAuthor().getName();

        // Get role color
        String roleColor = "§7";
        if (member != null) {
            List<Role> roles = member.getRoles();
            if (!roles.isEmpty()) {
                Role topRole = roles.get(0);
                if (topRole.getColor() != null) {
                    roleColor = ColorConverter.discordColorToMinecraft(topRole.getColor().getRGB());
                }
            }
        }

        // Format message for Minecraft
        String format = plugin.getConfigManager().getDiscordToMinecraftFormat();
        String formatted = format
                .replace("{username}", event.getAuthor().getName())
                .replace("{display-name}", displayName)
                .replace("{message}", MessageFormatter.formatForMinecraft(discordMessage))
                .replace("{role-color}", roleColor);

        formatted = MessageFormatter.colorize(formatted);

        final String finalMessage = formatted;

        // Broadcast to all online players on the main thread
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getServer().broadcastMessage(finalMessage);
            plugin.getPluginLogger().debug("Discord -> Minecraft: " + displayName + ": " + discordMessage);
        });
    }
}
