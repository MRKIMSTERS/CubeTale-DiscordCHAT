package com.cubetale.discordchat.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.lang.reflect.Method;
import java.net.URL;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Resolves the best possible avatar URL for a player.
 *
 * Resolution order:
 *   1. Skin texture hash extracted from the player's GameProfile
 *      (works for online players, SkinsRestorer, Geyser/Floodgate — anything
 *       that applies a skin via the textures property)
 *   2. SkinsRestorer 15.x API skin identifier  (reflection — no compile dep)
 *   3. SkinsRestorer 14.x API skin name        (reflection — no compile dep)
 *   4. Player UUID without hyphens             (matches mc-heads.net URL format)
 *
 * All URLs target https://mc-heads.net/avatar/{identifier}/{size}
 */
public class SkinsRestorerHook {

    private static final String MC_HEADS = "https://mc-heads.net/avatar/";

    private final Logger log;
    private final boolean srAvailable;

    private Object srApi15 = null;
    private Object playerStorage15 = null;
    private Object srApi14 = null;

    public SkinsRestorerHook(Plugin plugin) {
        this.log = plugin.getLogger();

        boolean hooked = false;
        if (plugin.getServer().getPluginManager().getPlugin("SkinsRestorer") != null) {
            hooked = tryHook15();
            if (!hooked) hooked = tryHook14();
            if (hooked) {
                log.info("[DiscordCHAT] SkinsRestorer hooked — SR skin names used as fallback.");
            } else {
                log.warning("[DiscordCHAT] SkinsRestorer found but API hook failed; will use GameProfile textures.");
            }
        }
        this.srAvailable = hooked;
    }

    // -------------------------------------------------------------------------
    // SR reflection hooks
    // -------------------------------------------------------------------------

    private boolean tryHook15() {
        try {
            Class<?> cls = Class.forName("net.skinsrestorer.api.SkinsRestorerProvider");
            srApi15 = cls.getMethod("get").invoke(null);
            playerStorage15 = srApi15.getClass().getMethod("getPlayerStorage").invoke(srApi15);
            return true;
        } catch (Exception e) {
            srApi15 = null;
            playerStorage15 = null;
            return false;
        }
    }

    private boolean tryHook14() {
        try {
            Class<?> cls = Class.forName("net.skinsrestorer.api.SkinsRestorerAPI");
            srApi14 = cls.getMethod("getApi").invoke(null);
            return true;
        } catch (Exception e) {
            srApi14 = null;
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Returns the best mc-heads.net avatar URL for the given player.
     * Must be called on the main server thread.
     *
     * @param player The online player
     * @param size   Pixel size (e.g. 128)
     * @return Full mc-heads.net URL guaranteed to be non-null
     */
    public String resolveAvatarUrl(Player player, int size) {

        // ── 1. Extract texture hash from the player's active GameProfile ──────
        //    SkinsRestorer writes the skin into this property when it applies
        //    a skin, so this works transparently with SR, Geyser, and online mode.
        try {
            PlayerProfile profile = player.getPlayerProfile();
            PlayerTextures textures = profile.getTextures();
            URL skinUrl = textures.getSkin();
            if (skinUrl != null) {
                String path = skinUrl.getPath(); // e.g. /texture/a1b2c3...
                String hash = path.substring(path.lastIndexOf('/') + 1);
                if (hash.length() > 10) {
                    log.fine("[DiscordCHAT] Avatar resolved via GameProfile texture hash for " + player.getName());
                    return MC_HEADS + hash + "/" + size;
                }
            }
        } catch (Exception e) {
            log.fine("[DiscordCHAT] GameProfile texture extraction failed: " + e.getMessage());
        }

        // ── 2. SkinsRestorer 15.x skin identifier ─────────────────────────────
        if (playerStorage15 != null) {
            String srName = resolveSkinName15(player.getUniqueId());
            if (srName != null) {
                log.fine("[DiscordCHAT] Avatar resolved via SR 15.x for " + player.getName() + " → " + srName);
                return MC_HEADS + stripHyphens(srName) + "/" + size;
            }
        }

        // ── 3. SkinsRestorer 14.x skin name ───────────────────────────────────
        if (srApi14 != null) {
            String srName = resolveSkinName14(player.getName());
            if (srName != null) {
                log.fine("[DiscordCHAT] Avatar resolved via SR 14.x for " + player.getName() + " → " + srName);
                return MC_HEADS + stripHyphens(srName) + "/" + size;
            }
        }

        // ── 4. UUID without hyphens (matches the mc-heads.net URL format) ──────
        String uuid = stripHyphens(player.getUniqueId().toString());
        log.fine("[DiscordCHAT] Avatar falling back to UUID for " + player.getName() + " → " + uuid);
        return MC_HEADS + uuid + "/" + size;
    }

    public boolean isAvailable() {
        return srAvailable;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private String resolveSkinName15(UUID uuid) {
        try {
            Method getSkin = playerStorage15.getClass().getMethod("getSkinOfPlayer", UUID.class);
            Optional<?> opt = (Optional<?>) getSkin.invoke(playerStorage15, uuid);
            if (opt == null || !opt.isPresent()) return null;
            Object skinId = opt.get();
            String id = (String) skinId.getClass().getMethod("getIdentifier").invoke(skinId);
            return (id != null && !id.isBlank()) ? id : null;
        } catch (Exception e) {
            log.fine("[DiscordCHAT] SR 15.x getSkin failed: " + e.getMessage());
            return null;
        }
    }

    private String resolveSkinName14(String playerName) {
        try {
            Method m = srApi14.getClass().getMethod("getSkinName", String.class);
            String name = (String) m.invoke(srApi14, playerName);
            return (name != null && !name.isBlank()) ? name : null;
        } catch (Exception e) {
            log.fine("[DiscordCHAT] SR 14.x getSkinName failed: " + e.getMessage());
            return null;
        }
    }

    /** Removes hyphens so UUID strings match the mc-heads.net URL format. */
    private static String stripHyphens(String s) {
        return s == null ? "" : s.replace("-", "");
    }
}
