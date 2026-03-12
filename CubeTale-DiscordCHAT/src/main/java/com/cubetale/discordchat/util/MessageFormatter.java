package com.cubetale.discordchat.util;

import org.bukkit.ChatColor;

import java.util.regex.Pattern;

public class MessageFormatter {

    private static final Pattern COLOR_PATTERN = Pattern.compile("(&|§)[0-9a-fk-orA-FK-OR]");
    private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("(&|§)#[0-9a-fA-F]{6}");
    private static final Pattern MARKDOWN_ESCAPE = Pattern.compile("([*_~`|\\\\])");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");

    /**
     * Strip all Minecraft color/formatting codes from a message.
     */
    public static String stripColors(String message) {
        if (message == null) return "";
        message = HEX_COLOR_PATTERN.matcher(message).replaceAll("");
        message = COLOR_PATTERN.matcher(message).replaceAll("");
        return ChatColor.stripColor(message);
    }

    /**
     * Translate Minecraft color codes (&a, &b, etc.) in a string.
     */
    public static String colorize(String message) {
        if (message == null) return "";
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    /**
     * Escape Discord markdown special characters in a message.
     */
    public static String escapeMarkdown(String message) {
        if (message == null) return "";
        return MARKDOWN_ESCAPE.matcher(message).replaceAll("\\\\$1");
    }

    /**
     * Format a Minecraft message for display in Discord.
     * Strips color codes and optionally escapes markdown.
     */
    public static String formatForDiscord(String message, boolean escapeMarkdown) {
        if (message == null) return "";
        message = stripColors(message);
        if (escapeMarkdown) {
            message = escapeMarkdown(message);
        }
        return message;
    }

    /**
     * Convert a Discord message for display in Minecraft.
     * Handles basic Discord formatting.
     */
    public static String formatForMinecraft(String message) {
        if (message == null) return "";
        // Remove Discord mentions formatting for readability
        message = message.replaceAll("<@!?\\d+>", "@user");
        message = message.replaceAll("<@&\\d+>", "@role");
        message = message.replaceAll("<#\\d+>", "#channel");
        // Replace Discord bold (**) with Minecraft bold (&l)
        message = message.replaceAll("\\*\\*(.+?)\\*\\*", "&l$1&r");
        // Replace Discord italic (*) with Minecraft italic (&o)
        message = message.replaceAll("\\*(.+?)\\*", "&o$1&r");
        // Replace Discord underline with Minecraft underline
        message = message.replaceAll("__(.+?)__", "&n$1&r");
        // Replace Discord strikethrough with Minecraft strikethrough
        message = message.replaceAll("~~(.+?)~~", "&m$1&r");
        return message;
    }

    /**
     * Truncate a message to a maximum length, appending "..." if needed.
     */
    public static String truncate(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength - 3) + "...";
    }

    /**
     * Check if a message contains a URL.
     */
    public static boolean containsUrl(String message) {
        if (message == null) return false;
        return URL_PATTERN.matcher(message).find();
    }

    /**
     * Format uptime from seconds to a human-readable string.
     */
    public static String formatUptime(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        if (days > 0) return days + "d " + hours + "h " + minutes + "m";
        if (hours > 0) return hours + "h " + minutes + "m";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    /**
     * Format TPS to a colored string.
     */
    public static String formatTps(double tps) {
        if (tps >= 19.0) return String.format("%.1f", tps);
        if (tps >= 15.0) return String.format("%.1f", tps);
        return String.format("%.1f", tps);
    }
}
