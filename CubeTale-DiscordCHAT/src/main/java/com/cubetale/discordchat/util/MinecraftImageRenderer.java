package com.cubetale.discordchat.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Renders Minecraft-style tooltip images using Java2D.
 * Used for item-display and player-list Discord embeds.
 */
public class MinecraftImageRenderer {

    // ── Palette (matches Minecraft's dark tooltip) ─────────────────────────────
    private static final Color BG            = new Color(16,  0, 16, 245);
    private static final Color BORDER_OUTER  = new Color(18, 10, 18);
    private static final Color BORDER_BRIGHT = new Color(80,  0, 160);
    private static final Color BORDER_DARK   = new Color(40,  0, 100);

    // Minecraft §-code → AWT Color (matches vanilla chat palette)
    private static final Map<Character, Color> CODE_COLORS = new LinkedHashMap<>();
    static {
        CODE_COLORS.put('0', new Color(0,   0,   0));
        CODE_COLORS.put('1', new Color(0,   0,   170));
        CODE_COLORS.put('2', new Color(0,   170, 0));
        CODE_COLORS.put('3', new Color(0,   170, 170));
        CODE_COLORS.put('4', new Color(170, 0,   0));
        CODE_COLORS.put('5', new Color(170, 0,   170));
        CODE_COLORS.put('6', new Color(255, 170, 0));
        CODE_COLORS.put('7', new Color(170, 170, 170));
        CODE_COLORS.put('8', new Color(85,  85,  85));
        CODE_COLORS.put('9', new Color(85,  85,  255));
        CODE_COLORS.put('a', new Color(85,  255, 85));
        CODE_COLORS.put('b', new Color(85,  255, 255));
        CODE_COLORS.put('c', new Color(255, 85,  85));
        CODE_COLORS.put('d', new Color(255, 85,  255));
        CODE_COLORS.put('e', new Color(255, 255, 85));
        CODE_COLORS.put('f', new Color(255, 255, 255));
    }
    private static final Color DEFAULT_COLOR = CODE_COLORS.get('f');

    private static final int PAD         = 12;
    private static final int LINE_H      = 20;
    private static final int FONT_SIZE   = 14;
    private static final int SCALE       = 2; // render at 2× then downscale for crisp text

    // ── Public API ──────────────────────────────────────────────────────────────

    /**
     * Renders a Minecraft-style item tooltip and returns PNG bytes.
     *
     * @param item       The ItemStack to render.
     * @param playerName Player name shown as owner label (e.g. "HollowNate's Item").
     * @return PNG bytes, or null if item is null/AIR.
     */
    public static byte[] renderItemTooltip(ItemStack item, String playerName) throws IOException {
        if (item == null || item.getType() == Material.AIR) return null;

        ItemMeta meta = item.getItemMeta();
        String ownerHeader = playerName + "'s Item";
        String itemHeader  = getItemDisplayName(item, meta);   // coloured
        List<String> lines = buildItemLines(item, meta);

        // ── measure ──────────────────────────────────────────────────────────
        Font font     = new Font(Font.MONOSPACED, Font.BOLD, FONT_SIZE * SCALE);
        Font fontPlain = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE * SCALE);
        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gd = dummy.createGraphics();
        gd.setFont(font);
        FontMetrics fmBold  = gd.getFontMetrics(font);
        FontMetrics fmPlain = gd.getFontMetrics(fontPlain);
        gd.dispose();

        int maxW = fmBold.stringWidth(ownerHeader);
        maxW = Math.max(maxW, measureRichWidth(itemHeader, fmBold));
        for (String line : lines) {
            maxW = Math.max(maxW, measureRichWidth(line, fmPlain));
        }

        int totalLines = 2 + lines.size(); // owner + item header + lines
        int w = maxW + PAD * 2 * SCALE + 8 * SCALE;
        int h = totalLines * LINE_H * SCALE + PAD * 2 * SCALE + LINE_H * SCALE; // extra gap after header

        w = Math.max(w, 240 * SCALE);

        // ── render at 2× ─────────────────────────────────────────────────────
        BufferedImage hiRes = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createGraphics(hiRes);

        drawBackground(g, w, h);

        int x  = PAD * SCALE + 4 * SCALE;
        int y  = PAD * SCALE + fmBold.getAscent();

        // Owner label (gray, small-ish)
        g.setFont(fontPlain);
        g.setColor(CODE_COLORS.get('7'));
        g.drawString(ownerHeader, x, y);
        y += LINE_H * SCALE;

        // Item name (coloured, bold)
        g.setFont(font);
        drawRichText(g, itemHeader, x, y, fmBold, true);
        y += (int)(LINE_H * SCALE * 1.5); // wider gap after item name

        // Detail lines (plain)
        g.setFont(fontPlain);
        for (String line : lines) {
            drawRichText(g, line, x, y, fmPlain, false);
            y += LINE_H * SCALE;
        }

        g.dispose();

        // ── downscale 2× → 1× ────────────────────────────────────────────────
        BufferedImage out = downscale(hiRes, w / SCALE, h / SCALE);
        return toPng(out);
    }

    /**
     * Renders a Minecraft-style online-players list and returns PNG bytes.
     */
    public static byte[] renderPlayerList(Collection<? extends Player> players, int maxPlayers) throws IOException {
        Font fontBold  = new Font(Font.MONOSPACED, Font.BOLD,  FONT_SIZE * SCALE);
        Font fontPlain = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE * SCALE);

        BufferedImage dummy = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D gd = dummy.createGraphics();
        FontMetrics fmBold  = gd.getFontMetrics(fontBold);
        FontMetrics fmPlain = gd.getFontMetrics(fontPlain);
        gd.dispose();

        // Build rows: {prefixColoured, playerName, ping}
        List<Object[]> rows = new ArrayList<>();
        for (Player p : players) {
            String prefix = resolvePrefix(p);
            rows.add(new Object[]{ prefix, p.getName(), p.getPing() });
        }

        String title = "Online Players (" + players.size() + "/" + maxPlayers + ")";
        int maxW = fmBold.stringWidth(title);
        for (Object[] row : rows) {
            String cleanPrefix = strip(row[0].toString());
            String name        = (String) row[1];
            String bars        = pingBars((int) row[2]);
            int rw = fmBold.stringWidth(cleanPrefix + " " + name + "  " + bars);
            if (rw > maxW) maxW = rw;
        }

        int w = maxW + PAD * 2 * SCALE + 8 * SCALE;
        int h = (rows.size() + 2) * LINE_H * SCALE + PAD * 2 * SCALE + 8 * SCALE;
        w = Math.max(w, 280 * SCALE);
        h = Math.max(h, 60 * SCALE);

        BufferedImage hiRes = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createGraphics(hiRes);
        drawBackground(g, w, h);

        int x = PAD * SCALE + 4 * SCALE;
        int y = PAD * SCALE + fmBold.getAscent();

        // Title
        g.setFont(fontBold);
        g.setColor(CODE_COLORS.get('a')); // bright green
        g.drawString(title, x, y);
        y += (int)(LINE_H * SCALE * 1.3);

        // Separator line
        g.setColor(new Color(60, 60, 60));
        g.drawLine(x, y - LINE_H * SCALE / 3, w - PAD * SCALE, y - LINE_H * SCALE / 3);

        // Player rows
        for (Object[] row : rows) {
            String prefix = (String) row[0];
            String name   = (String) row[1];
            int    ping   = (int)    row[2];
            String bars   = pingBars(ping);
            Color  barCol = pingColor(ping);

            int cx = x;

            // Rank prefix
            String cleanPrefix = strip(prefix).trim();
            if (!cleanPrefix.isEmpty()) {
                Color prefCol = firstColor(prefix);
                g.setFont(fontBold);
                g.setColor(prefCol);
                g.drawString(cleanPrefix, cx, y);
                cx += fmBold.stringWidth(cleanPrefix + " ");
            }

            // Player name
            g.setFont(fontPlain);
            g.setColor(DEFAULT_COLOR);
            g.drawString(name, cx, y);
            cx += fmPlain.stringWidth(name + "  ");

            // Ping bars
            g.setFont(fontBold);
            g.setColor(barCol);
            g.drawString(bars, cx, y);

            y += LINE_H * SCALE;
        }

        if (rows.isEmpty()) {
            g.setFont(fontPlain);
            g.setColor(CODE_COLORS.get('7'));
            g.drawString("No players online.", x, y);
        }

        g.dispose();

        BufferedImage out = downscale(hiRes, w / SCALE, h / SCALE);
        return toPng(out);
    }

    // ── Drawing helpers ─────────────────────────────────────────────────────────

    private static Graphics2D createGraphics(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void drawBackground(Graphics2D g, int w, int h) {
        // Fill dark background
        g.setColor(BG);
        g.fillRoundRect(0, 0, w, h, 8, 8);

        // Outer frame
        g.setColor(BORDER_OUTER);
        g.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);

        // Inner gradient border (bright top-left, dark bottom-right)
        g.setColor(BORDER_BRIGHT);
        g.drawLine(2, 1,   w - 3, 1);    // top
        g.drawLine(1, 2,   1, h - 3);    // left

        g.setColor(BORDER_DARK);
        g.drawLine(2,   h - 2, w - 3, h - 2); // bottom
        g.drawLine(w - 2, 2,   w - 2, h - 3); // right
    }

    /** Draws text that may contain §-colour codes. Returns the final x offset. */
    private static int drawRichText(Graphics2D g, String text, int x, int y, FontMetrics fm, boolean bold) {
        Color cur = DEFAULT_COLOR;
        int   i   = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                char code = Character.toLowerCase(text.charAt(i + 1));
                Color mapped = CODE_COLORS.get(code);
                if (mapped != null) cur = mapped;
                else if (code == 'r') cur = DEFAULT_COLOR;
                i += 2;
            } else {
                String ch = String.valueOf(c);
                g.setColor(cur);
                g.drawString(ch, x, y);
                x += fm.stringWidth(ch);
                i++;
            }
        }
        return x;
    }

    private static int measureRichWidth(String text, FontMetrics fm) {
        int w = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) {
                i += 2;
            } else {
                w += fm.charWidth(c);
                i++;
            }
        }
        return w;
    }

    private static BufferedImage downscale(BufferedImage src, int tw, int th) {
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, tw, th, null);
        g.dispose();
        return out;
    }

    private static byte[] toPng(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", baos);
        return baos.toByteArray();
    }

    // ── Item tooltip content ────────────────────────────────────────────────────

    private static String getItemDisplayName(ItemStack item, ItemMeta meta) {
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        return "§f" + formatMaterialName(item.getType());
    }

    private static List<String> buildItemLines(ItemStack item, ItemMeta meta) {
        List<String> lines = new ArrayList<>();

        // Enchantments
        if (meta != null && !meta.getEnchants().isEmpty()) {
            Map<Enchantment, Integer> enchants = new LinkedHashMap<>(meta.getEnchants());
            for (Map.Entry<Enchantment, Integer> e : enchants.entrySet()) {
                String name  = toTitleCase(e.getKey().getKey().getKey().replace('_', ' '));
                String level = e.getValue() > 1 ? " " + toRoman(e.getValue()) : "";
                lines.add("§9" + name + level);
            }
        }

        // Lore (custom rarity / stats text usually lives here)
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) {
                if (!lines.isEmpty() && !lore.isEmpty()) lines.add(""); // spacer
                lines.addAll(lore);
            }
        }

        // Attribute line (When in Main Hand) — plain metadata
        if (lines.isEmpty() || !containsWhenInHand(lines)) {
            String attackDmg = getAttributeValue(item, "generic.attack_damage");
            String attackSpd = getAttributeValue(item, "generic.attack_speed");
            if (attackDmg != null || attackSpd != null) {
                lines.add("");
                lines.add("§7When in Main Hand:");
                if (attackDmg != null) lines.add("§a " + attackDmg + " Attack Damage");
                if (attackSpd != null) lines.add("§a " + attackSpd + " Attack Speed");
            }
        }

        // Durability
        if (meta instanceof Damageable) {
            int maxDur = item.getType().getMaxDurability();
            if (maxDur > 0) {
                int cur = maxDur - ((Damageable) meta).getDamage();
                lines.add("");
                lines.add("§7Durability: §f" + cur + " / " + maxDur);
            }
        }

        // Material key
        lines.add("§8" + item.getType().getKey().toString());

        // Component count
        int components = countComponents(meta);
        if (components > 0) {
            lines.add("§8" + components + " component(s)");
        }

        return lines;
    }

    private static boolean containsWhenInHand(List<String> lines) {
        for (String l : lines) if (strip(l).toLowerCase().contains("when in")) return true;
        return false;
    }

    /** Best-effort attribute reading via Spigot AttributeModifier API (1.20.5+). */
    private static String getAttributeValue(ItemStack item, String attrName) {
        try {
            org.bukkit.attribute.Attribute attr = org.bukkit.attribute.Attribute.valueOf(
                    attrName.replace('.', '_').toUpperCase());
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            Collection<org.bukkit.attribute.AttributeModifier> mods =
                    meta.getAttributeModifiers(attr);
            if (mods == null || mods.isEmpty()) return null;
            double sum = 0;
            for (org.bukkit.attribute.AttributeModifier mod : mods) sum += mod.getAmount();
            return String.format("%.1f", sum);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static int countComponents(ItemMeta meta) {
        if (meta == null) return 0;
        int c = 0;
        if (meta.hasDisplayName())  c++;
        if (meta.hasLore())         c++;
        if (meta.hasEnchants())     c += meta.getEnchants().size();
        if (meta.hasCustomModelData()) c++;
        if (meta instanceof Damageable && ((Damageable) meta).hasDamage()) c++;
        return c;
    }

    // ── Player-list helpers ─────────────────────────────────────────────────────

    private static String resolvePrefix(Player player) {
        try {
            net.luckperms.api.LuckPerms lp = Bukkit.getServicesManager()
                    .getRegistration(net.luckperms.api.LuckPerms.class).getProvider();
            net.luckperms.api.model.user.User user = lp.getUserManager().getUser(player.getUniqueId());
            if (user != null) {
                String prefix = user.getCachedData().getMetaData().getPrefix();
                if (prefix != null && !prefix.isEmpty()) return prefix;
                String group = user.getPrimaryGroup();
                return group != null ? "§7" + group.toUpperCase() : "";
            }
        } catch (Exception ignored) {}
        return "";
    }

    private static String pingBars(int ping) {
        if (ping <   0) return "▌▌▌▌▌";
        if (ping <  50) return "▌▌▌▌▌";
        if (ping < 100) return "▌▌▌▌░";
        if (ping < 150) return "▌▌▌░░";
        if (ping < 250) return "▌▌░░░";
        if (ping < 500) return "▌░░░░";
        return "░░░░░";
    }

    private static Color pingColor(int ping) {
        if (ping < 100) return CODE_COLORS.get('a'); // green
        if (ping < 200) return CODE_COLORS.get('e'); // yellow
        if (ping < 400) return CODE_COLORS.get('6'); // gold
        return CODE_COLORS.get('c');                 // red
    }

    // ── Utility ─────────────────────────────────────────────────────────────────

    public static String strip(String text) {
        if (text == null) return "";
        return text.replaceAll("[§&][0-9a-fk-orA-FK-OR]", "");
    }

    private static Color firstColor(String text) {
        if (text == null) return DEFAULT_COLOR;
        for (int i = 0; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            if (c == '§' || c == '&') {
                Color mapped = CODE_COLORS.get(Character.toLowerCase(text.charAt(i + 1)));
                if (mapped != null) return mapped;
            }
        }
        return DEFAULT_COLOR;
    }

    private static String formatMaterialName(Material m) {
        return toTitleCase(m.getKey().getKey().replace('_', ' '));
    }

    private static String toTitleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String word : s.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)))
                  .append(word.substring(1).toLowerCase())
                  .append(' ');
            }
        }
        return sb.toString().trim();
    }

    private static String toRoman(int n) {
        String[] r = {"", "I","II","III","IV","V","VI","VII","VIII","IX","X"};
        return n > 0 && n < r.length ? r[n] : String.valueOf(n);
    }
}
