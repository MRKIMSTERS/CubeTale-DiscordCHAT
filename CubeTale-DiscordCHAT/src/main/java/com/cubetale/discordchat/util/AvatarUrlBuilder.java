package com.cubetale.discordchat.util;

import org.bukkit.entity.Player;

import java.util.UUID;

public class AvatarUrlBuilder {

    private final String service;
    private final int size;
    private final boolean overlay;

    public AvatarUrlBuilder(String service, int size, boolean overlay) {
        this.service = service == null ? "crafatar" : service.toLowerCase();
        this.size = size;
        this.overlay = overlay;
    }

    public String getAvatarUrl(Player player) {
        return getAvatarUrl(player.getUniqueId(), player.getName());
    }

    public String getAvatarUrl(UUID uuid, String playerName) {
        switch (service) {
            case "mcheads":
            case "mc-heads":
                return "https://mc-heads.net/avatar/" + uuid + "/" + size;
            case "cravatar":
                return "https://cravatar.eu/helmavatar/" + playerName + "/" + size + ".png";
            case "minotar":
                return "https://minotar.net/helm/" + playerName + "/" + size + ".png";
            case "crafatar":
            default:
                return "https://crafatar.com/avatars/" + uuid + "?size=" + size + (overlay ? "&overlay" : "");
        }
    }

    public String getHeadUrl(Player player) {
        return getHeadUrl(player.getUniqueId(), player.getName());
    }

    public String getHeadUrl(UUID uuid, String playerName) {
        switch (service) {
            case "crafatar":
            default:
                return "https://crafatar.com/renders/head/" + uuid + "?size=" + size + (overlay ? "&overlay" : "");
            case "mcheads":
            case "mc-heads":
                return "https://mc-heads.net/head/" + uuid + "/" + size;
            case "cravatar":
                return "https://cravatar.eu/helmavatar/" + playerName + "/" + size + ".png";
            case "minotar":
                return "https://minotar.net/helm/" + playerName + "/" + size + ".png";
        }
    }

    /**
     * Returns a face-only avatar URL (just the face, no helmet layer).
     */
    public String getFaceUrl(UUID uuid, String playerName) {
        switch (service) {
            case "crafatar":
            default:
                return "https://crafatar.com/avatars/" + uuid + "?size=" + size;
            case "mcheads":
            case "mc-heads":
                return "https://mc-heads.net/avatar/" + uuid + "/" + size;
            case "minotar":
                return "https://minotar.net/avatar/" + playerName + "/" + size + ".png";
        }
    }

    public String getService() {
        return service;
    }

    public int getSize() {
        return size;
    }

    public boolean isOverlay() {
        return overlay;
    }
}
