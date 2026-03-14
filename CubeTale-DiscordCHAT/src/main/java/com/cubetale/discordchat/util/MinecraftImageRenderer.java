package com.cubetale.discordchat.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders Minecraft-style images using Java2D.
 * Covers: item tooltips, inventory grids, player-list panels, and profile cards.
 */
public class MinecraftImageRenderer {

    // ── Palette ─────────────────────────────────────────────────────────────────
    private static final Color BG            = new Color(16,  0, 16, 245);
    private static final Color BORDER_OUTER  = new Color(18, 10, 18);
    private static final Color BORDER_BRIGHT = new Color(80,  0, 160);
    private static final Color BORDER_DARK   = new Color(40,  0, 100);

    static final Map<Character, Color> CODE_COLORS = new LinkedHashMap<>();
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
    static final Color DEFAULT_COLOR = CODE_COLORS.get('f');

    private static final int PAD       = 12;
    private static final int LINE_H    = 20;
    private static final int FONT_SIZE = 14;
    private static final int SCALE     = 2;

    // ── Texture cache (item sprites from mc.nerothe.com) ────────────────────────
    private static final Map<String, BufferedImage> TEXTURE_CACHE   = new ConcurrentHashMap<>();
    private static final Set<String>                FAILED_TEXTURES = Collections.synchronizedSet(new HashSet<>());

    private static BufferedImage fetchTexture(String materialKey) {
        if (FAILED_TEXTURES.contains(materialKey)) return null;
        BufferedImage cached = TEXTURE_CACHE.get(materialKey);
        if (cached != null) return cached;
        try {
            URL url = new URL("https://mc.nerothe.com/img/1.21/" + materialKey + ".png");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent", "CubeTale-DiscordCHAT/1.0");
            BufferedImage img = ImageIO.read(conn.getInputStream());
            if (img != null) { TEXTURE_CACHE.put(materialKey, img); return img; }
        } catch (Exception ignored) {}
        FAILED_TEXTURES.add(materialKey);
        return null;
    }

    // ── Public API ──────────────────────────────────────────────────────────────

    /** Renders an item tooltip (the [item] feature). */
    public static byte[] renderItemTooltip(ItemStack item, String playerName) throws IOException {
        if (item == null || item.getType() == Material.AIR) return null;

        ItemMeta meta        = item.getItemMeta();
        String ownerHeader   = playerName + "'s Item";
        String itemHeader    = getItemDisplayName(item, meta);
        List<String> lines   = buildItemLines(item, meta);

        Font fontBold  = new Font(Font.MONOSPACED, Font.BOLD,  FONT_SIZE * SCALE);
        Font fontPlain = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE * SCALE);
        FontMetrics fmBold  = dummyFm(fontBold);
        FontMetrics fmPlain = dummyFm(fontPlain);

        int maxW = fmBold.stringWidth(ownerHeader);
        maxW = Math.max(maxW, measureRichWidth(itemHeader, fmBold));
        for (String line : lines) maxW = Math.max(maxW, measureRichWidth(line, fmPlain));

        int w = Math.max(maxW + PAD * 2 * SCALE + 8 * SCALE, 240 * SCALE);
        int h = (2 + lines.size()) * LINE_H * SCALE + PAD * 2 * SCALE + LINE_H * SCALE;

        BufferedImage hi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createG(hi);
        drawBg(g, w, h);

        int x = PAD * SCALE + 4 * SCALE;
        int y = PAD * SCALE + fmBold.getAscent();

        g.setFont(fontPlain);
        g.setColor(CODE_COLORS.get('7'));
        g.drawString(ownerHeader, x, y);
        y += LINE_H * SCALE;

        g.setFont(fontBold);
        drawRichText(g, itemHeader, x, y, fmBold);
        y += (int)(LINE_H * SCALE * 1.5);

        g.setFont(fontPlain);
        for (String line : lines) { drawRichText(g, line, x, y, fmPlain); y += LINE_H * SCALE; }

        g.dispose();
        return toPng(downscale(hi, w / SCALE, h / SCALE));
    }

    /** Renders the /players list image. */
    public static byte[] renderPlayerList(Collection<? extends Player> players, int maxPlayers) throws IOException {
        Font fontBold  = new Font(Font.MONOSPACED, Font.BOLD,  FONT_SIZE * SCALE);
        Font fontPlain = new Font(Font.MONOSPACED, Font.PLAIN, FONT_SIZE * SCALE);
        FontMetrics fmBold  = dummyFm(fontBold);
        FontMetrics fmPlain = dummyFm(fontPlain);

        List<Object[]> rows = new ArrayList<>();
        for (Player p : players) rows.add(new Object[]{ resolvePrefix(p), p.getName(), p.getPing() });

        String title = "Online Players (" + players.size() + "/" + maxPlayers + ")";
        int maxW = fmBold.stringWidth(title);
        for (Object[] row : rows) {
            int rw = fmBold.stringWidth(strip(row[0].toString()).trim() + " " + row[1] + "  " + pingBars((int) row[2]));
            if (rw > maxW) maxW = rw;
        }

        int w = Math.max(maxW + PAD * 2 * SCALE + 8 * SCALE, 280 * SCALE);
        int h = Math.max((rows.size() + 2) * LINE_H * SCALE + PAD * 2 * SCALE + 8 * SCALE, 60 * SCALE);

        BufferedImage hi = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createG(hi);
        drawBg(g, w, h);

        int x = PAD * SCALE + 4 * SCALE;
        int y = PAD * SCALE + fmBold.getAscent();

        g.setFont(fontBold);
        g.setColor(CODE_COLORS.get('a'));
        g.drawString(title, x, y);
        y += (int)(LINE_H * SCALE * 1.3);

        g.setColor(new Color(60, 60, 60));
        g.drawLine(x, y - LINE_H * SCALE / 3, w - PAD * SCALE, y - LINE_H * SCALE / 3);

        for (Object[] row : rows) {
            String prefix    = (String) row[0];
            String name      = (String) row[1];
            int    ping      = (int)    row[2];
            int    cx        = x;

            String cleanPfx = strip(prefix).trim();
            if (!cleanPfx.isEmpty()) {
                g.setFont(fontBold);
                g.setColor(firstColor(prefix));
                g.drawString(cleanPfx, cx, y);
                cx += fmBold.stringWidth(cleanPfx + " ");
            }

            g.setFont(fontPlain);
            g.setColor(DEFAULT_COLOR);
            g.drawString(name, cx, y);
            cx += fmPlain.stringWidth(name + "  ");

            g.setFont(fontBold);
            g.setColor(pingColor(ping));
            g.drawString(pingBars(ping), cx, y);
            y += LINE_H * SCALE;
        }
        if (rows.isEmpty()) {
            g.setFont(fontPlain); g.setColor(CODE_COLORS.get('7'));
            g.drawString("No players online.", x, y);
        }

        g.dispose();
        return toPng(downscale(hi, w / SCALE, h / SCALE));
    }

    /**
     * Renders a player's full inventory as a Minecraft-style grid image.
     * Fetches real item textures from mc.nerothe.com with in-memory caching.
     *
     * Slot layout:
     *   Left column  : Armor (39=helmet, 38=chest, 37=leggings, 36=boots) + Offhand (40)
     *   Right section: Main inventory (9–35) rows 1–3, Hotbar (0–8) row 4
     */
    public static byte[] renderInventory(ItemStack[] contents, String playerName) throws IOException {
        // Pre-fetch all needed textures (already on async thread)
        Set<String> needed = new LinkedHashSet<>();
        for (ItemStack it : contents) {
            if (it != null && it.getType() != Material.AIR)
                needed.add(it.getType().getKey().getKey().toLowerCase());
        }
        Map<String, BufferedImage> tex = new HashMap<>();
        for (String key : needed) { BufferedImage t = fetchTexture(key); if (t != null) tex.put(key, t); }

        final int S   = 36 * SCALE; // slot size (px at 2×)
        final int G   = 3  * SCALE; // gap between slots
        final int P   = PAD * SCALE;
        final int HDR = 26 * SCALE; // header height

        // Separator between armor column and main grid
        final int SEP = 10 * SCALE;

        // x positions
        int armorX  = P;
        int mainX   = armorX + S + SEP;
        // width = P + armorCol + SEP + 9*(S+G)-G + P
        int totalW  = mainX + 9 * (S + G) - G + P;

        // y rows: header, then 4 inventory rows, then offhand row
        int y0      = P + HDR + G * 2;
        // 4 main rows (armor/main/hotbar) + offhand gap
        int totalH  = y0 + 4 * (S + G) + G + P;

        // Row y values
        int[] rowY = new int[4];
        for (int i = 0; i < 4; i++) rowY[i] = y0 + i * (S + G);
        int offhandY = rowY[3]; // offhand sits beside boots

        BufferedImage hi = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createG(hi);
        drawBg(g, totalW, totalH);

        // Header
        Font fontBold = new Font(Font.MONOSPACED, Font.BOLD, FONT_SIZE * SCALE);
        g.setFont(fontBold);
        g.setColor(CODE_COLORS.get('7'));
        g.drawString(playerName + "'s Inventory", P, P + dummyFm(fontBold).getAscent());

        // Separator line under header
        g.setColor(new Color(60, 60, 60));
        g.drawLine(P, P + HDR, totalW - P, P + HDR);

        // ── Armor slots (left column) ──────────────────────────────────────────
        int[] armorSlots = { 39, 38, 37, 36 }; // helmet→boots
        for (int i = 0; i < 4; i++) {
            ItemStack item = safeGet(contents, armorSlots[i]);
            drawSlot(g, item, armorX, rowY[i], S, false, tex);
        }

        // ── Offhand slot ──────────────────────────────────────────────────────
        // Rendered below armor, centered under it
        drawSlot(g, safeGet(contents, 40), armorX, offhandY + S + G, S, false, tex);

        // ── Main inventory (rows 0–2) ──────────────────────────────────────────
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = 9 + row * 9 + col;
                int sx   = mainX + col * (S + G);
                drawSlot(g, safeGet(contents, slot), sx, rowY[row], S, false, tex);
            }
        }

        // ── Hotbar (row 3, highlighted) ────────────────────────────────────────
        for (int col = 0; col < 9; col++) {
            int sx = mainX + col * (S + G);
            drawSlot(g, safeGet(contents, col), sx, rowY[3], S, true, tex);
        }

        // ── Labels ─────────────────────────────────────────────────────────────
        Font small = new Font(Font.MONOSPACED, Font.PLAIN, (int)(FONT_SIZE * SCALE * 0.65));
        FontMetrics smFm = dummyFm(small);
        g.setFont(small);
        g.setColor(CODE_COLORS.get('8'));

        String armorLbl  = "Armor";
        String mainLbl   = "Inventory";
        String hotbarLbl = "Hotbar";

        g.drawString(armorLbl,  armorX,  rowY[0] - smFm.getDescent() - G / 2);
        g.drawString(mainLbl,   mainX,   rowY[0] - smFm.getDescent() - G / 2);
        // Hotbar label centred
        int hotbarLblX = mainX + (9 * (S + G) - smFm.stringWidth(hotbarLbl)) / 2;
        g.drawString(hotbarLbl, hotbarLblX, rowY[3] - smFm.getDescent() - G / 2);

        g.dispose();
        return toPng(downscale(hi, totalW / SCALE, totalH / SCALE));
    }

    /**
     * Renders a player's equipped armor (helmet, chestplate, leggings, boots) and
     * offhand item as a vertical strip of Minecraft-style item tooltip images.
     *
     * armorSlots index mapping (Bukkit order): 0=boots, 1=leggings, 2=chestplate, 3=helmet
     * Displayed top-to-bottom as: Helmet, Chestplate, Leggings, Boots, [Offhand if present]
     */
    public static byte[] renderArmor(ItemStack[] armorSlots, ItemStack offhand, String playerName) throws IOException {
        ItemStack helmet     = safeGet(armorSlots, 3);
        ItemStack chestplate = safeGet(armorSlots, 2);
        ItemStack leggings   = safeGet(armorSlots, 1);
        ItemStack boots      = safeGet(armorSlots, 0);

        final int S   = 36 * SCALE;
        final int G   = 4  * SCALE;
        final int P   = PAD * SCALE;
        final int HDR = 26 * SCALE;

        ItemStack[] display = { helmet, chestplate, leggings, boots, offhand };
        String[] labels     = { "Helmet", "Chestplate", "Leggings", "Boots", "Offhand" };

        Set<String> needed = new LinkedHashSet<>();
        for (ItemStack it : display) {
            if (it != null && it.getType() != Material.AIR)
                needed.add(it.getType().getKey().getKey().toLowerCase());
        }
        Map<String, BufferedImage> tex = new HashMap<>();
        for (String key : needed) { BufferedImage t = fetchTexture(key); if (t != null) tex.put(key, t); }

        Font fontBold  = new Font(Font.MONOSPACED, Font.BOLD,  FONT_SIZE * SCALE);
        Font fontSmall = new Font(Font.MONOSPACED, Font.PLAIN, (int)(FONT_SIZE * SCALE * 0.65));
        FontMetrics fmBold  = dummyFm(fontBold);
        FontMetrics fmSmall = dummyFm(fontSmall);

        int labelW = 0;
        for (String lbl : labels) labelW = Math.max(labelW, fmSmall.stringWidth(lbl));

        int colSlot  = P;
        int colLabel = colSlot + S + G;
        int totalW   = colLabel + labelW + P;
        int totalH   = P + HDR + G + display.length * (S + G) + P;

        BufferedImage hi = new BufferedImage(totalW, totalH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createG(hi);
        drawBg(g, totalW, totalH);

        g.setFont(fontBold);
        g.setColor(CODE_COLORS.get('7'));
        g.drawString(playerName + "'s Armor", P, P + fmBold.getAscent());

        g.setColor(new Color(60, 60, 60));
        g.drawLine(P, P + HDR, totalW - P, P + HDR);

        int y0 = P + HDR + G;
        for (int i = 0; i < display.length; i++) {
            int slotY = y0 + i * (S + G);
            drawSlot(g, display[i], colSlot, slotY, S, false, tex);
            g.setFont(fontSmall);
            g.setColor(display[i] != null && display[i].getType() != Material.AIR
                    ? CODE_COLORS.get('f') : CODE_COLORS.get('8'));
            g.drawString(labels[i], colLabel, slotY + S / 2 + fmSmall.getAscent() / 2);
        }

        g.dispose();
        return toPng(downscale(hi, totalW / SCALE, totalH / SCALE));
    }

    /**
     * Renders a player profile card (the /profile command).
     * Fetches the player's head sprite from mc-heads.net.
     */
    public static byte[] renderProfileCard(String playerName, String uuid,
                                           String rankPrefix, boolean online,
                                           int deaths, int kills, int mobKills,
                                           long playedTicks) throws IOException {
        // ── Fetch head ────────────────────────────────────────────────────────
        BufferedImage head = null;
        try {
            URL url = new URL("https://mc-heads.net/head/" + uuid + "/100");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("User-Agent", "CubeTale-DiscordCHAT/1.0");
            head = ImageIO.read(conn.getInputStream());
        } catch (Exception ignored) {}

        // ── Layout constants ──────────────────────────────────────────────────
        final int HEAD_SIZE  = 100 * SCALE;
        final int CARD_W     = 480 * SCALE;
        final int CARD_H     = 180 * SCALE;
        final int P          = PAD * SCALE;

        BufferedImage hi = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = createG(hi);

        // Background: slightly lighter than tooltip, dark blue-gray
        g.setColor(new Color(20, 20, 30, 245));
        g.fillRoundRect(0, 0, CARD_W, CARD_H, 10, 10);
        g.setColor(BORDER_OUTER);
        g.drawRoundRect(0, 0, CARD_W - 1, CARD_H - 1, 10, 10);
        g.setColor(BORDER_BRIGHT);
        g.drawLine(2, 1, CARD_W - 3, 1);
        g.drawLine(1, 2, 1, CARD_H - 3);
        g.setColor(BORDER_DARK);
        g.drawLine(2, CARD_H - 2, CARD_W - 3, CARD_H - 2);
        g.drawLine(CARD_W - 2, 2, CARD_W - 2, CARD_H - 3);

        // ── Player head ───────────────────────────────────────────────────────
        int headX = P;
        int headY = (CARD_H - HEAD_SIZE) / 2;
        if (head != null) {
            g.drawImage(head, headX, headY, HEAD_SIZE, HEAD_SIZE, null);
            // Subtle glow / shadow around head
            g.setColor(new Color(80, 0, 160, 60));
            g.drawRect(headX - 2, headY - 2, HEAD_SIZE + 3, HEAD_SIZE + 3);
        } else {
            // Placeholder
            g.setColor(new Color(60, 60, 60));
            g.fillRect(headX, headY, HEAD_SIZE, HEAD_SIZE);
            g.setColor(CODE_COLORS.get('7'));
            g.setFont(new Font(Font.MONOSPACED, Font.BOLD, 30 * SCALE));
            g.drawString("?", headX + HEAD_SIZE / 2 - 10 * SCALE, headY + HEAD_SIZE / 2 + 10 * SCALE);
        }

        // Vertical divider
        int divX = headX + HEAD_SIZE + P;
        g.setColor(new Color(80, 0, 160, 120));
        g.fillRect(divX, P, 2 * SCALE, CARD_H - 2 * P);

        // ── Text section ───────────────────────────────────────────────────────
        int tx   = divX + P;
        int ty   = P;
        Font nameFont  = new Font(Font.MONOSPACED, Font.BOLD,  18 * SCALE);
        Font rankFont  = new Font(Font.MONOSPACED, Font.BOLD,  13 * SCALE);
        Font statFont  = new Font(Font.MONOSPACED, Font.PLAIN, 12 * SCALE);
        FontMetrics nmFm   = dummyFm(nameFont);
        FontMetrics rkFm   = dummyFm(rankFont);
        FontMetrics stFm   = dummyFm(statFont);

        ty += nmFm.getAscent();

        // Player name
        g.setFont(nameFont);
        g.setColor(DEFAULT_COLOR);
        g.drawString(playerName, tx, ty);
        ty += nmFm.getHeight() + (4 * SCALE);

        // Rank (coloured)
        if (rankPrefix != null && !rankPrefix.isEmpty()) {
            g.setFont(rankFont);
            drawRichText(g, rankPrefix, tx, ty, rkFm);
            ty += rkFm.getHeight() + (4 * SCALE);
        }

        // Online status
        Color statusColor = online ? CODE_COLORS.get('a') : CODE_COLORS.get('c');
        String statusText = online ? "● Online" : "● Offline";
        g.setFont(rankFont);
        g.setColor(statusColor);
        g.drawString(statusText, tx, ty);
        ty += rkFm.getHeight() + (10 * SCALE);

        // Separator
        g.setColor(new Color(60, 60, 60));
        g.drawLine(tx, ty, CARD_W - P, ty);
        ty += (8 * SCALE);

        // Stats
        g.setFont(statFont);
        long playedSec = playedTicks / 20;
        String played = formatPlaytime(playedSec);
        String[][] stats = {
            { "☠ Deaths",     String.valueOf(deaths)  },
            { "⚔ Player Kills", String.valueOf(kills) },
            { "🗡 Mob Kills",  String.valueOf(mobKills) },
            { "⏱ Time Played", played                  }
        };

        int colW = (CARD_W - tx - P) / 2;
        int statY = ty + stFm.getAscent();
        for (int i = 0; i < stats.length; i++) {
            int col = i % 2;
            int row = i / 2;
            int sx  = tx + col * colW;
            int sy  = statY + row * (stFm.getHeight() + 4 * SCALE);

            g.setColor(CODE_COLORS.get('7'));
            g.drawString(stats[i][0] + ":", sx, sy);

            g.setColor(CODE_COLORS.get('e'));
            g.drawString(stats[i][1], sx + stFm.stringWidth(stats[i][0] + ": "), sy);
        }

        g.dispose();
        return toPng(downscale(hi, CARD_W / SCALE, CARD_H / SCALE));
    }

    // ── Slot drawing ─────────────────────────────────────────────────────────────

    private static void drawSlot(Graphics2D g, ItemStack item, int x, int y, int size,
                                  boolean hotbar, Map<String, BufferedImage> textures) {
        // Background
        Color bgCol = hotbar ? new Color(60, 55, 20, 200) : new Color(35, 35, 35, 200);
        g.setColor(bgCol);
        g.fillRect(x, y, size, size);

        // Border
        if (hotbar) {
            g.setColor(new Color(200, 180, 60));
            g.drawRect(x, y, size, size);
        } else {
            g.setColor(new Color(55, 55, 55));
            g.drawRect(x, y, size, size);
            g.setColor(new Color(20, 20, 20));
            g.drawLine(x + 1,        y + size,     x + size,    y + size);
            g.drawLine(x + size,     y + 1,         x + size,   y + size);
        }

        if (item == null || item.getType() == Material.AIR) return;

        String matKey = item.getType().getKey().getKey().toLowerCase();
        int pad = (int)(size * 0.06);
        int inner = size - 2 * pad;
        BufferedImage tex = textures.get(matKey);

        if (tex != null) {
            // Draw real texture
            Graphics2D imgG = (Graphics2D) g.create();
            imgG.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            imgG.drawImage(tex, x + pad, y + pad, inner, inner, null);
            imgG.dispose();
        } else {
            // Fallback: coloured block
            g.setColor(materialColor(item.getType()));
            g.fillRect(x + pad, y + pad, inner, inner);
        }

        // Stack count
        if (item.getAmount() > 1) {
            Font countFont = new Font(Font.MONOSPACED, Font.BOLD, (int)(size * 0.28));
            g.setFont(countFont);
            FontMetrics fm = g.getFontMetrics();
            String cnt     = String.valueOf(item.getAmount());
            int cx = x + size - fm.stringWidth(cnt) - 2 * SCALE;
            int cy = y + size - fm.getDescent();

            // Shadow
            g.setColor(Color.BLACK);
            g.drawString(cnt, cx + SCALE, cy + SCALE);
            g.setColor(Color.WHITE);
            g.drawString(cnt, cx, cy);
        }
    }

    private static ItemStack safeGet(ItemStack[] contents, int index) {
        if (index < 0 || index >= contents.length) return null;
        return contents[index];
    }

    // ── Drawing helpers ─────────────────────────────────────────────────────────

    private static Graphics2D createG(BufferedImage img) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        return g;
    }

    private static void drawBg(Graphics2D g, int w, int h) {
        g.setColor(BG);
        g.fillRoundRect(0, 0, w, h, 8, 8);
        g.setColor(BORDER_OUTER);
        g.drawRoundRect(0, 0, w - 1, h - 1, 8, 8);
        g.setColor(BORDER_BRIGHT);
        g.drawLine(2, 1, w - 3, 1);
        g.drawLine(1, 2, 1, h - 3);
        g.setColor(BORDER_DARK);
        g.drawLine(2, h - 2, w - 3, h - 2);
        g.drawLine(w - 2, 2, w - 2, h - 3);
    }

    static int drawRichText(Graphics2D g, String text, int x, int y, FontMetrics fm) {
        Color cur = DEFAULT_COLOR;
        int i = 0;
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
        int w = 0, i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if ((c == '§' || c == '&') && i + 1 < text.length()) { i += 2; }
            else { w += fm.charWidth(c); i++; }
        }
        return w;
    }

    private static FontMetrics dummyFm(Font font) {
        BufferedImage d = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        return d.createGraphics().getFontMetrics(font);
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

    // ── Item tooltip helpers ─────────────────────────────────────────────────────

    private static String getItemDisplayName(ItemStack item, ItemMeta meta) {
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return "§f" + formatMaterialName(item.getType());
    }

    private static List<String> buildItemLines(ItemStack item, ItemMeta meta) {
        List<String> lines = new ArrayList<>();
        if (meta != null && !meta.getEnchants().isEmpty()) {
            for (Map.Entry<Enchantment, Integer> e : new LinkedHashMap<>(meta.getEnchants()).entrySet()) {
                String name  = toTitleCase(e.getKey().getKey().getKey().replace('_', ' '));
                String level = e.getValue() > 1 ? " " + toRoman(e.getValue()) : "";
                lines.add("§9" + name + level);
            }
        }
        if (meta != null && meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null) { if (!lines.isEmpty()) lines.add(""); lines.addAll(lore); }
        }
        if (!containsWhenInHand(lines)) {
            String dmg = getAttrValue(item, "GENERIC_ATTACK_DAMAGE");
            String spd = getAttrValue(item, "GENERIC_ATTACK_SPEED");
            if (dmg != null || spd != null) {
                lines.add(""); lines.add("§7When in Main Hand:");
                if (dmg != null) lines.add("§a " + dmg + " Attack Damage");
                if (spd != null) lines.add("§a " + spd + " Attack Speed");
            }
        }
        if (meta instanceof Damageable) {
            int max = item.getType().getMaxDurability();
            if (max > 0) {
                int cur = max - ((Damageable) meta).getDamage();
                lines.add(""); lines.add("§7Durability: §f" + cur + " / " + max);
            }
        }
        lines.add("§8" + item.getType().getKey());
        int comps = countComponents(meta);
        if (comps > 0) lines.add("§8" + comps + " component(s)");
        return lines;
    }

    private static boolean containsWhenInHand(List<String> l) {
        for (String s : l) if (strip(s).toLowerCase().contains("when in")) return true;
        return false;
    }

    private static String getAttrValue(ItemStack item, String attrName) {
        try {
            org.bukkit.attribute.Attribute attr = org.bukkit.attribute.Attribute.valueOf(attrName);
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;
            Collection<org.bukkit.attribute.AttributeModifier> mods = meta.getAttributeModifiers(attr);
            if (mods == null || mods.isEmpty()) return null;
            double sum = 0;
            for (org.bukkit.attribute.AttributeModifier m : mods) sum += m.getAmount();
            return String.format("%.1f", sum);
        } catch (Exception ignored) { return null; }
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

    // ── Player list / stats helpers ──────────────────────────────────────────────

    public static String resolvePrefix(Player player) {
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

    public static String resolveOfflinePrefix(OfflinePlayer player) {
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
        if (ping < 100) return CODE_COLORS.get('a');
        if (ping < 200) return CODE_COLORS.get('e');
        if (ping < 400) return CODE_COLORS.get('6');
        return CODE_COLORS.get('c');
    }

    // ── Material → colour fallback (for when texture fetch fails) ───────────────

    private static Color materialColor(Material m) {
        String key = m.getKey().getKey();
        if (key.contains("diamond"))   return new Color(70,  220, 220);
        if (key.contains("netherite")) return new Color(60,  40,  60);
        if (key.contains("gold"))      return new Color(255, 210, 40);
        if (key.contains("iron"))      return new Color(200, 200, 200);
        if (key.contains("emerald"))   return new Color(30,  200, 80);
        if (key.contains("redstone"))  return new Color(200, 40,  40);
        if (key.contains("oak") || key.contains("spruce") || key.contains("birch") ||
            key.contains("jungle") || key.contains("acacia") || key.contains("dark_oak"))
            return new Color(140, 100, 50);
        if (key.contains("stone") || key.contains("cobble") || key.contains("gravel"))
            return new Color(100, 100, 100);
        if (key.contains("dirt") || key.contains("sand"))
            return new Color(150, 120, 80);
        if (key.contains("grass"))     return new Color(80,  160, 60);
        if (key.contains("water"))     return new Color(40,  80,  200);
        if (key.contains("lava"))      return new Color(230, 100, 20);
        if (key.contains("obsidian"))  return new Color(20,  10,  30);
        if (key.contains("glass"))     return new Color(180, 220, 240, 150);
        if (key.contains("bone") || key.contains("skull")) return new Color(230, 220, 190);
        if (key.contains("ender"))     return new Color(50,  10,  80);
        if (key.contains("nether"))    return new Color(100, 30,  30);
        return new Color(100, 100, 120);
    }

    // ── Utility ─────────────────────────────────────────────────────────────────

    public static String strip(String text) {
        if (text == null) return "";
        return text.replaceAll("[§&][0-9a-fk-orA-FK-OR]", "");
    }

    static Color firstColor(String text) {
        if (text == null) return DEFAULT_COLOR;
        for (int i = 0; i < text.length() - 1; i++) {
            char c = text.charAt(i);
            if (c == '§' || c == '&') {
                Color m = CODE_COLORS.get(Character.toLowerCase(text.charAt(i + 1)));
                if (m != null) return m;
            }
        }
        return DEFAULT_COLOR;
    }

    private static String formatMaterialName(Material m) {
        return toTitleCase(m.getKey().getKey().replace('_', ' '));
    }

    private static String toTitleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.split(" ")) {
            if (!w.isEmpty())
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(' ');
        }
        return sb.toString().trim();
    }

    private static String toRoman(int n) {
        String[] r = {"","I","II","III","IV","V","VI","VII","VIII","IX","X"};
        return n > 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private static String formatPlaytime(long seconds) {
        long d = seconds / 86400, h = (seconds % 86400) / 3600, m = (seconds % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return m + "m";
    }
}
