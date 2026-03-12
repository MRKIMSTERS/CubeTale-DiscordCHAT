package com.cubetale.discordchat.util;

import java.awt.*;

public class ColorConverter {

    /**
     * Convert a hex color string (#RRGGBB or RRGGBB) to an integer for Discord embeds.
     */
    public static int hexToInt(String hex) {
        if (hex == null || hex.isEmpty()) return 0x7289DA; // Discord blurple default
        hex = hex.replace("#", "").trim();
        try {
            return Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            return 0x7289DA;
        }
    }

    /**
     * Convert a Discord role color integer to a hex string.
     */
    public static String intToHex(int color) {
        return String.format("#%06X", color & 0xFFFFFF);
    }

    /**
     * Convert an AWT Color to a Discord embed color integer.
     */
    public static int awtToInt(Color color) {
        if (color == null) return 0x7289DA;
        return color.getRGB() & 0xFFFFFF;
    }

    /**
     * Map a Discord role color to the nearest Minecraft chat color.
     */
    public static String discordColorToMinecraft(int discordColor) {
        Color c = new Color(discordColor);
        int r = c.getRed();
        int g = c.getGreen();
        int b = c.getBlue();

        // Simple nearest-color mapping to Minecraft palette
        if (r > 200 && g < 100 && b < 100) return "§c"; // red
        if (r > 200 && g > 100 && b < 100) return "§6"; // gold
        if (r > 200 && g > 200 && b < 100) return "§e"; // yellow
        if (r < 100 && g > 150 && b < 100) return "§a"; // green
        if (r < 100 && g < 100 && b > 150) return "§9"; // blue
        if (r > 100 && g < 100 && b > 150) return "§5"; // purple
        if (r < 100 && g > 150 && b > 150) return "§b"; // aqua
        if (r > 150 && g < 100 && b > 100) return "§d"; // light purple
        if (r > 150 && g > 150 && b > 150) return "§7"; // gray
        if (r < 80 && g < 80 && b < 80) return "§8"; // dark gray
        return "§f"; // white as default
    }
}
