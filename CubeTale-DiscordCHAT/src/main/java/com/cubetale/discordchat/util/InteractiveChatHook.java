package com.cubetale.discordchat.util;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Soft-dependency hook for LOOHP's InteractiveChat plugin.
 *
 * Provides:
 *  - Detection of whether InteractiveChat is installed and its API accessible.
 *  - Retrieval of all registered IC placeholder keywords (including custom ones).
 *  - Message stripping to remove IC placeholder tokens before forwarding to Discord.
 *  - Whether a given message contains any active IC placeholder for a player.
 *
 * Everything is done via reflection so there is no compile-time dependency.
 *
 * InteractiveChat GitHub: https://github.com/LOOHP/InteractiveChat
 */
public class InteractiveChatHook {

    /** Built-in trigger literals we always handle, even without IC installed. */
    public static final String TRIGGER_ITEM       = "[item]";
    public static final String TRIGGER_INV        = "[inv]";
    public static final String TRIGGER_ENDERCHEST = "[enderchest]";
    public static final String TRIGGER_EC_SHORT   = "[ec]";
    public static final String TRIGGER_ARMOR      = "[armor]";
    public static final String TRIGGER_OFFHAND    = "[offhand]";
    public static final String TRIGGER_MAP        = "[map]";
    public static final String TRIGGER_BOOK       = "[book]";

    private final Logger log;
    private final boolean available;

    /** Compiled patterns for all IC-registered placeholders (custom + built-in). */
    private final List<Pattern> icPatterns = new ArrayList<>();

    /** Cached regex-string keyword list (for debug/config display). */
    private final List<String> icKeywords = new ArrayList<>();

    public InteractiveChatHook(Plugin plugin) {
        this.log = plugin.getLogger();

        if (plugin.getServer().getPluginManager().getPlugin("InteractiveChat") == null) {
            this.available = false;
            log.info("[DiscordCHAT] InteractiveChat not found — using built-in [item]/[inv]/[enderchest] triggers.");
            return;
        }

        boolean hooked = tryHookApi();
        this.available = hooked;

        if (hooked) {
            log.info("[DiscordCHAT] InteractiveChat hooked — " + icKeywords.size()
                    + " placeholder(s) registered: " + icKeywords);
        } else {
            log.warning("[DiscordCHAT] InteractiveChat found but API hook failed. "
                    + "Using built-in triggers only.");
        }
    }

    // ── API hook (reflection) ─────────────────────────────────────────────────

    private boolean tryHookApi() {
        try {
            Class<?> apiClass = Class.forName("com.loohp.interactivechat.api.InteractiveChatAPI");

            // IC 4.x: InteractiveChatAPI.getRegisteredICPlaceholders() → Collection<ICPlaceholder>
            Method getPlaceholders = null;
            for (String methodName : new String[]{"getRegisteredICPlaceholders", "getICPlaceholders", "getPlaceholders"}) {
                try {
                    getPlaceholders = apiClass.getMethod(methodName);
                    break;
                } catch (NoSuchMethodException ignored) {}
            }

            if (getPlaceholders == null) return false;

            Collection<?> placeholders = (Collection<?>) getPlaceholders.invoke(null);
            if (placeholders == null) return false;

            for (Object ph : placeholders) {
                String keyword = extractKeyword(ph);
                if (keyword != null && !keyword.isBlank()) {
                    icKeywords.add(keyword);
                    try {
                        icPatterns.add(Pattern.compile(keyword, Pattern.CASE_INSENSITIVE));
                    } catch (PatternSyntaxException ignored) {
                        // If the keyword is a plain literal, wrap it in a literal pattern
                        icPatterns.add(Pattern.compile(Pattern.quote(keyword), Pattern.CASE_INSENSITIVE));
                    }
                }
            }
            return true;
        } catch (Exception e) {
            log.fine("[DiscordCHAT] IC API hook failed: " + e.getMessage());
            return false;
        }
    }

    private String extractKeyword(Object icPlaceholder) {
        for (String m : new String[]{"getKeyword", "keyword", "getPattern", "getRegex", "getText"}) {
            try {
                Object result = icPlaceholder.getClass().getMethod(m).invoke(icPlaceholder);
                if (result instanceof String) return (String) result;
                if (result instanceof Pattern)  return ((Pattern) result).pattern();
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** @return true if InteractiveChat is installed and its API was hooked successfully. */
    public boolean isAvailable() { return available; }

    /**
     * Returns true if the given message contains any InteractiveChat placeholder
     * (built-in or custom) that should be processed and rendered for Discord.
     */
    public boolean containsAnyTrigger(String message) {
        if (message == null) return false;
        String lower = message.toLowerCase();
        // Always check built-ins
        if (lower.contains(TRIGGER_ITEM) || lower.contains(TRIGGER_INV)
                || lower.contains(TRIGGER_ENDERCHEST) || lower.contains(TRIGGER_EC_SHORT)) {
            return true;
        }
        // Check IC-registered custom placeholders
        for (Pattern p : icPatterns) {
            if (p.matcher(message).find()) return true;
        }
        return false;
    }

    /**
     * Checks whether the message contains a specific built-in trigger.
     */
    public static boolean hasTrigger(String message, String trigger) {
        return message != null && message.toLowerCase().contains(trigger.toLowerCase());
    }

    /**
     * Strips all recognized IC placeholder tokens from a message so the plain text
     * forwarded to Discord does not contain the literal "[item]" etc.
     *
     * Built-ins are removed. IC-registered keyword patterns are also removed.
     * The returned string is trimmed.
     */
    public String stripTriggers(String message) {
        if (message == null) return "";
        // Built-ins
        message = message.replaceAll("(?i)\\[item\\]",       "");
        message = message.replaceAll("(?i)\\[inv\\]",        "");
        message = message.replaceAll("(?i)\\[inventory\\]",  "");
        message = message.replaceAll("(?i)\\[enderchest\\]", "");
        message = message.replaceAll("(?i)\\[ec\\]",         "");
        message = message.replaceAll("(?i)\\[armor\\]",      "");
        message = message.replaceAll("(?i)\\[offhand\\]",    "");
        message = message.replaceAll("(?i)\\[map\\]",        "");
        message = message.replaceAll("(?i)\\[book\\]",       "");

        // IC-registered custom patterns
        for (Pattern p : icPatterns) {
            message = p.matcher(message).replaceAll("");
        }

        // Collapse multiple spaces
        return message.replaceAll("\\s{2,}", " ").trim();
    }

    /**
     * Returns the list of all IC placeholder keyword strings that are currently loaded.
     * Includes only custom IC-registered ones (not built-ins).
     */
    public List<String> getIcKeywords() {
        return new ArrayList<>(icKeywords);
    }
}
