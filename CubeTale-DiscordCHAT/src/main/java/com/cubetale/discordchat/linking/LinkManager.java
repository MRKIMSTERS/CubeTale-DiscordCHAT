package com.cubetale.discordchat.linking;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import com.cubetale.discordchat.database.DatabaseManager;
import net.dv8tion.jda.api.entities.User;
import org.bukkit.entity.Player;

import java.security.SecureRandom;
import java.util.UUID;

public class LinkManager {

    private final CubeTaleDiscordChat plugin;
    private final DatabaseManager db;
    private final SecureRandom random = new SecureRandom();

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public LinkManager(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        this.db = plugin.getDatabaseManager();
    }

    /**
     * Generate a verification code for a player and store it in the database.
     * Returns the generated code.
     */
    public String generateCode(Player player) {
        int length = plugin.getConfigManager().getCodeLength();
        int expirySeconds = plugin.getConfigManager().getCodeExpiry();

        StringBuilder code = new StringBuilder();
        for (int i = 0; i < length; i++) {
            code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
        }

        long expiresAt = System.currentTimeMillis() + (expirySeconds * 1000L);
        db.saveVerificationCode(player.getUniqueId(), code.toString(), expiresAt);

        plugin.getPluginLogger().debug("Generated link code for " + player.getName() + ": " + code);
        return code.toString();
    }

    /**
     * Link a Discord account using a verification code.
     * Returns true if successful.
     */
    public boolean linkWithCode(String code, String discordId, String discordTag) {
        UUID minecraftUUID = db.getUUIDForCode(code.toUpperCase());
        if (minecraftUUID == null) {
            plugin.getPluginLogger().debug("Invalid/expired code attempt: " + code + " by " + discordTag);
            return false;
        }

        // Check if this Discord account is already linked
        if (db.isDiscordLinked(discordId)) {
            plugin.getPluginLogger().debug("Discord account already linked: " + discordTag);
            return false;
        }

        // Get minecraft name from the online player list or use UUID
        String minecraftName = minecraftUUID.toString();
        org.bukkit.OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(minecraftUUID);
        if (offlinePlayer.getName() != null) {
            minecraftName = offlinePlayer.getName();
        }

        boolean success = db.linkAccount(minecraftUUID, minecraftName, discordId, discordTag);
        if (success) {
            // Delete used verification code
            db.deleteVerificationCodes(minecraftUUID);

            // Notify the player if they are online
            Player onlinePlayer = plugin.getServer().getPlayer(minecraftUUID);
            if (onlinePlayer != null) {
                onlinePlayer.sendMessage(plugin.getMessagesConfig().get("link.success",
                        "discord", discordTag));
            }

            // Send Discord DM notification
            sendLinkSuccessNotification(discordId, minecraftName, minecraftUUID);

            // Sync roles if enabled
            if (plugin.getConfigManager().isRoleSyncEnabled()) {
                plugin.getRoleSyncManager().syncPlayer(minecraftUUID);
            }

            plugin.getPluginLogger().info("Linked " + minecraftName + " (" + minecraftUUID + ") to Discord " + discordTag);
        }

        return success;
    }

    /**
     * Unlink a player from their Discord account.
     */
    public boolean unlinkPlayer(UUID minecraftUUID) {
        return db.unlinkAccount(minecraftUUID);
    }

    /**
     * Send a Discord DM with the verification code.
     */
    public void sendCodeDM(String discordId, String code, int expirySeconds) {
        if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) return;

        try {
            User user = plugin.getDiscordBot().getJda().retrieveUserById(discordId).complete();
            if (user == null) return;

            String dmMessage = "**CubeTale Account Verification**\n\n"
                    + "Your verification code is: `" + code + "`\n\n"
                    + "Use this command in Minecraft: `/link " + code + "`\n"
                    + "This code expires in **" + expirySeconds + " seconds**.";

            user.openPrivateChannel().queue(channel ->
                    channel.sendMessage(dmMessage).queue(
                            s -> plugin.getPluginLogger().debug("Sent verification DM to " + user.getAsTag()),
                            e -> plugin.getPluginLogger().warning("Failed to send DM to " + user.getAsTag() + ": " + e.getMessage())
                    )
            );
        } catch (Exception e) {
            plugin.getPluginLogger().warning("Failed to send DM: " + e.getMessage());
        }
    }

    /**
     * Send a Discord DM confirming successful link.
     */
    private void sendLinkSuccessNotification(String discordId, String minecraftName, UUID minecraftUUID) {
        if (plugin.getDiscordBot() == null || !plugin.getDiscordBot().isConnected()) return;

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                User user = plugin.getDiscordBot().getJda().retrieveUserById(discordId).complete();
                if (user == null) return;

                String message = "✅ **Account Linked Successfully!**\n\n"
                        + "Your Discord account has been linked to Minecraft player **" + minecraftName + "**.\n"
                        + "Use `/unlink` in Minecraft or `/link` in Discord to manage your link.";

                user.openPrivateChannel().queue(channel ->
                        channel.sendMessage(message).queue()
                );
            } catch (Exception e) {
                plugin.getPluginLogger().debug("Failed to send link success DM: " + e.getMessage());
            }
        });
    }

}
