package com.cubetale.discordchat.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Soft-dependency hook for SkinsRestorer.
 *
 * Works with both the old 14.x API and the modern 15.x API using pure reflection,
 * so no compile-time dependency on SkinsRestorer is needed.
 *
 * Resolution order:
 *   1. SkinsRestorer 15.x  (net.skinsrestorer.api.SkinsRestorerProvider)
 *   2. SkinsRestorer 14.x  (net.skinsrestorer.api.SkinsRestorerAPI)
 *   3. Fallback: mc-heads.net/{uuid}
 */
public class SkinsRestorerHook {

    private static final String MC_HEADS = "https://mc-heads.net/avatar/";

    private final Logger log;
    private final boolean available;

    private Object srApi15 = null;
    private Object playerStorage15 = null;

    private Object srApi14 = null;

    public SkinsRestorerHook(Plugin plugin) {
        this.log = plugin.getLogger();

        if (plugin.getServer().getPluginManager().getPlugin("SkinsRestorer") == null) {
            this.available = false;
            return;
        }

        boolean hooked = tryHook15();
        if (!hooked) hooked = tryHook14();

        this.available = hooked;
        if (available) {
            log.info("[DiscordCHAT] SkinsRestorer hooked — player skin avatars will be resolved correctly.");
        } else {
            log.warning("[DiscordCHAT] SkinsRestorer found but API hook failed. Avatars will fall back to UUID lookup.");
        }
    }

    private boolean tryHook15() {
        try {
            Class<?> providerClass = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            srApi15 = providerClass.getMethod("get").invoke(null);
            playerStorage15 = srApi15.getClass().getMethod("getPlayerStorage").invoke(srApi15);
            return true;
        } catch (Exception ignored) {
            srApi15 = null;
            playerStorage15 = null;
            return false;
        }
    }

    private boolean tryHook14() {
        try {
            Class<?> apiClass = Class.forName("net.skinsrestorer.api.SkinsRestorerAPI");
            srApi14 = apiClass.getMethod("getApi").invoke(null);
            return true;
        } catch (Exception ignored) {
            srApi14 = null;
            return false;
        }
    }

    /**
     * Returns whether SkinsRestorer is present and successfully hooked.
     */
    public boolean isAvailable() {
        return available;
    }

    /**
     * Resolves the best skin name for the given player:
     *  - If SkinsRestorer is active and has a skin set, returns that skin's identifier (Minecraft username).
     *  - Otherwise returns null (caller should fall back to player UUID / username).
     *
     * MUST be called on the main server thread (event handler context is fine).
     */
    public String resolveSkinName(Player player) {
        if (!available) return null;

        if (playerStorage15 != null) {
            return resolveSkinName15(player.getUniqueId());
        }
        if (srApi14 != null) {
            return resolveSkinName14(player.getName());
        }
        return null;
    }

    private String resolveSkinName15(UUID uuid) {
        try {
            Method getSkin = playerStorage15.getClass().getMethod("getSkinOfPlayer", UUID.class);
            Optional<?> opt = (Optional<?>) getSkin.invoke(playerStorage15, uuid);
            if (opt == null || !opt.isPresent()) return null;
            Object skinId = opt.get();
            String identifier = (String) skinId.getClass().getMethod("getIdentifier").invoke(skinId);
            return (identifier != null && !identifier.isBlank()) ? identifier : null;
        } catch (Exception e) {
            log.fine("[DiscordCHAT] SR 15.x getSkin failed: " + e.getMessage());
            return null;
        }
    }

    private String resolveSkinName14(String playerName) {
        try {
            Method getSkinName = srApi14.getClass().getMethod("getSkinName", String.class);
            String skinName = (String) getSkinName.invoke(srApi14, playerName);
            return (skinName != null && !skinName.isBlank()) ? skinName : null;
        } catch (Exception e) {
            log.fine("[DiscordCHAT] SR 14.x getSkinName failed: " + e.getMessage());
            return null;
        }
    }

    /**
     * Builds the best possible mc-heads.net avatar URL for a player.
     *
     * If SkinsRestorer has a skin configured, the skin name (Minecraft username) is used
     * so the correct texture is returned even for offline/cracked/Bedrock players.
     *
     * @param player  The player
     * @param size    Pixel size (e.g. 128)
     * @return Full mc-heads.net avatar URL
     */
    public String resolveAvatarUrl(Player player, int size) {
        String skinName = resolveSkinName(player);
        if (skinName != null) {
            return MC_HEADS + skinName + "/" + size;
        }
        return MC_HEADS + player.getUniqueId() + "/" + size;
    }

    /**
     * Builds a mc-heads.net avatar URL from a skin name override or UUID fallback.
     * Use this when you already have the skin name stored as a String (async-safe).
     *
     * @param skinNameOrUuid  Skin username (from SR) or player UUID string
     * @param size            Pixel size
     * @return Full URL
     */
    public static String buildUrl(String skinNameOrUuid, int size) {
        return MC_HEADS + skinNameOrUuid + "/" + size;
    }
}
