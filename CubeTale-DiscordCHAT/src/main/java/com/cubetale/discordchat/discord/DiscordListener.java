package com.cubetale.discordchat.discord;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.util.ColorConverter;
import com.cubetale.discordchat.util.MessageFormatter;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

public class DiscordListener extends ListenerAdapter {

    private final CubeTaleDiscordChat plugin;

    public DiscordListener(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.getAuthor().isSystem()) return;

        String chatChannelId = plugin.getConfigManager().getChatChannelId();
        if (chatChannelId == null || chatChannelId.isEmpty()) return;
        if (!event.getChannel().getId().equals(chatChannelId)) return;

        if (!plugin.getConfigManager().isChatSyncEnabled()) return;
        if (!plugin.getConfigManager().isDiscordToMinecraftEnabled()) return;

        String discordMessage = event.getMessage().getContentDisplay();
        if (discordMessage.isEmpty()) return;

        Member member = event.getMember();
        String displayName = member != null ? member.getEffectiveName() : event.getAuthor().getName();

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

        // ── Reply quoting ─────────────────────────────────────────────────────
        String quotePrefix = "";
        if (plugin.getConfigManager().isReplyQuotingEnabled()
                && event.getMessage().getMessageReference() != null) {
            try {
                Message referenced = event.getMessage().getReferencedMessage();
                if (referenced != null) {
                    String quotedAuthor = referenced.getAuthor().getName();
                    String quotedText   = referenced.getContentDisplay();
                    if (quotedText.length() > 60) quotedText = quotedText.substring(0, 60) + "…";
                    if (!quotedText.isEmpty()) {
                        quotePrefix = "§8> §7" + quotedAuthor + ": " + quotedText;
                    }
                }
            } catch (Exception ignored) {}
        }

        String format = plugin.getConfigManager().getDiscordToMinecraftFormat();
        String formatted = format
                .replace("{username}", event.getAuthor().getName())
                .replace("{display-name}", displayName)
                .replace("{message}", MessageFormatter.formatForMinecraft(discordMessage))
                .replace("{role-color}", roleColor);

        formatted = MessageFormatter.colorize(formatted);

        final String finalMessage = formatted;
        final String finalQuote   = quotePrefix;
        final String rawDiscord   = discordMessage;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!finalQuote.isEmpty()) {
                plugin.getServer().broadcastMessage(MessageFormatter.colorize(finalQuote));
            }
            plugin.getServer().broadcastMessage(finalMessage);
            plugin.getPluginLogger().debug("Discord -> Minecraft: " + displayName + ": " + rawDiscord);
        });

        // ── @mention → in-game notification ───────────────────────────────────
        if (plugin.getConfigManager().isMentionNotifyEnabled()) {
            handleMentions(event.getMessage(), displayName);
        }
    }

    /**
     * For every user mentioned in the Discord message, check if they are linked
     * to an online Minecraft player. If so, send a title and play a sound.
     */
    private void handleMentions(Message message, String senderDisplayName) {
        List<User> mentions = message.getMentions().getUsers();
        if (mentions.isEmpty()) return;

        for (User mentioned : mentions) {
            UUID mcUuid = plugin.getDatabaseManager().getMinecraftUUID(mentioned.getId());
            if (mcUuid == null) continue;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player target = plugin.getServer().getPlayer(mcUuid);
                if (target == null || !target.isOnline()) return;

                target.sendTitle("§e✉ Mentioned!", "§fby " + senderDisplayName, 10, 60, 20);
                target.sendMessage("§e" + senderDisplayName + " §fmentioned you on Discord!");
                try {
                    target.playSound(target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.8f);
                } catch (Exception ignored) {}
                plugin.getPluginLogger().debug("Mention notification sent to " + target.getName());
            });
        }
    }
}
