package com.cubetale.discordchat.linking;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the verification flow for Minecraft ↔ Discord account linking.
 *
 * <p>Flow (CODE method):
 * <ol>
 *   <li>Player runs {@code /link} in Minecraft → {@link #requestCode(Player)} is called.</li>
 *   <li>A random code is generated and stored in the database via {@link LinkManager}.</li>
 *   <li>The code is displayed to the player (and optionally sent via Discord DM).</li>
 *   <li>Player runs {@code /link <code>} in Discord → {@link LinkManager#linkWithCode} is called.</li>
 * </ol>
 */
public class VerificationManager {

    private final CubeTaleDiscordChat plugin;
    private final LinkManager linkManager;

    /** Rate-limit: tracks the last time each UUID requested a code (epoch ms). */
    private final Map<UUID, Long> lastRequestTime = new ConcurrentHashMap<>();

    /** Minimum milliseconds between code requests from the same player (30 s). */
    private static final long RATE_LIMIT_MS = 30_000L;

    public VerificationManager(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        this.linkManager = plugin.getLinkManager();
    }

    /**
     * Handle a player's request for a new verification code.
     *
     * @param player the requesting player
     * @return {@code true} if a code was successfully generated and shown
     */
    public boolean requestCode(Player player) {
        UUID uuid = player.getUniqueId();

        if (!plugin.getConfigManager().isLinkingEnabled()) {
            player.sendMessage(plugin.getMessagesConfig().get("link.disabled"));
            return false;
        }

        // Already linked?
        if (plugin.getDatabaseManager().isLinked(uuid)) {
            String discordTag = plugin.getDatabaseManager().getDiscordTag(uuid);
            player.sendMessage(plugin.getMessagesConfig().get("link.already-linked",
                    "discord", discordTag != null ? discordTag : "Unknown"));
            return false;
        }

        // Rate-limit check
        long now = System.currentTimeMillis();
        Long lastRequest = lastRequestTime.get(uuid);
        if (lastRequest != null && (now - lastRequest) < RATE_LIMIT_MS) {
            long remainingSeconds = (RATE_LIMIT_MS - (now - lastRequest)) / 1000;
            player.sendMessage(plugin.getMessagesConfig().get("link.rate-limited",
                    "seconds", String.valueOf(remainingSeconds)));
            return false;
        }

        lastRequestTime.put(uuid, now);

        String code = linkManager.generateCode(player);
        int expirySeconds = plugin.getConfigManager().getCodeExpiry();

        // Show code in chat
        player.sendMessage(plugin.getMessagesConfig().get("link.prompt",
                "code", code,
                "time", String.valueOf(expirySeconds)));

        plugin.getPluginLogger().debug("Verification code for " + player.getName() + ": " + code);
        return true;
    }

    /**
     * Clear the rate-limit entry for a player (e.g. after a successful link or on logout).
     */
    public void clearRateLimit(UUID uuid) {
        lastRequestTime.remove(uuid);
    }

    /**
     * Clear all cached rate-limit data (called on plugin shutdown or reload).
     */
    public void clearAll() {
        lastRequestTime.clear();
    }
}
