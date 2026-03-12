package com.cubetale.discordchat.util;

import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Builds player avatar/head image URLs using various skin-render CDN services.
 *
 * Default service: mc-heads  (https://mc-heads.net)
 * Example URL   : https://mc-heads.net/avatar/e66479cd-6b57-4ffb-9551-d41c2a883a55/128
 */
public class AvatarUrlBuilder {

    private static final String MC_HEADS_BASE = "https://mc-heads.net";

    private final String service;
    private final int size;
    private final boolean overlay;

    public AvatarUrlBuilder(String service, int size, boolean overlay) {
        this.service = (service == null || service.isBlank()) ? "mcheads" : service.toLowerCase().trim();
        this.size = size > 0 ? size : 128;
        this.overlay = overlay;
    }

    /** Convenience overload — looks up UUID and name from the live Player object. */
    public String getAvatarUrl(Player player) {
        return getAvatarUrl(player.getUniqueId(), player.getName());
    }

    /**
     * Returns a flat face (with hat layer) avatar URL.
     * Used as the webhook avatar and for embed thumbnails.
     */
    public String getAvatarUrl(UUID uuid, String playerName) {
        switch (service) {
            case "crafatar":
                return "https://crafatar.com/avatars/" + uuid
                        + "?size=" + size + (overlay ? "&overlay" : "");
            case "cravatar":
                return "https://cravatar.eu/helmavatar/" + playerName + "/" + size + ".png";
            case "minotar":
                return "https://minotar.net/helm/" + playerName + "/" + size + ".png";
            case "mcheads":
            case "mc-heads":
            default:
                return MC_HEADS_BASE + "/avatar/" + uuid + "/" + size;
        }
    }

    /** Convenience overload. */
    public String getHeadUrl(Player player) {
        return getHeadUrl(player.getUniqueId(), player.getName());
    }

    /**
     * Returns a head/helmet render URL.
     * Used for larger featured images in embeds (e.g. death, advancement).
     * Falls back to the same avatar URL if the service doesn't have a distinct head endpoint.
     */
    public String getHeadUrl(UUID uuid, String playerName) {
        switch (service) {
            case "crafatar":
                return "https://crafatar.com/renders/head/" + uuid
                        + "?size=" + size + (overlay ? "&overlay" : "");
            case "cravatar":
                return "https://cravatar.eu/helmavatar/" + playerName + "/" + size + ".png";
            case "minotar":
                return "https://minotar.net/helm/" + playerName + "/" + size + ".png";
            case "mcheads":
            case "mc-heads":
            default:
                return MC_HEADS_BASE + "/avatar/" + uuid + "/" + size;
        }
    }

    /**
     * Returns a face-only avatar URL (no hat/helmet layer).
     */
    public String getFaceUrl(UUID uuid, String playerName) {
        switch (service) {
            case "crafatar":
                return "https://crafatar.com/avatars/" + uuid + "?size=" + size;
            case "minotar":
                return "https://minotar.net/avatar/" + playerName + "/" + size + ".png";
            case "cravatar":
                return "https://cravatar.eu/helmavatar/" + playerName + "/" + size + ".png";
            case "mcheads":
            case "mc-heads":
            default:
                return MC_HEADS_BASE + "/avatar/" + uuid + "/" + size;
        }
    }

    /**
     * Build a static mc-heads avatar URL directly from a UUID string.
     * Safe to call from any context — no Player or Server reference required.
     */
    public static String mcHeadsUrl(UUID uuid, int size) {
        return MC_HEADS_BASE + "/avatar/" + uuid + "/" + size;
    }

    /** @return active service identifier (lowercase). */
    public String getService() { return service; }

    /** @return configured render size in pixels. */
    public int getSize() { return size; }

    /** @return whether the helmet/overlay layer is requested. */
    public boolean isOverlay() { return overlay; }
}
