package com.cubetale.icaddon.render;

import com.cubetale.icaddon.CubeTaleICAddon;
import com.cubetale.icaddon.config.AddonConfig;
import com.cubetale.icaddon.hook.DiscordCHATBridge;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core dispatch logic.
 *
 * Given a player and a raw chat message this class:
 *  1. Detects which InteractiveChat trigger tokens are present.
 *  2. Schedules a synchronous Bukkit task to capture inventory state (inventory
 *     must be read on the main thread).
 *  3. Hands off to {@link DiscordCHATBridge} which calls CubeTale-DiscordCHAT's
 *     WebhookManager to render and send the images to Discord.
 *
 * De-duplication: The IC event and the Bukkit chat event can both fire for the
 * same message. A per-player flag set (cleared after 500 ms / 10 ticks) prevents
 * the same trigger being dispatched twice when both listeners are active.
 *
 * When {@code icPrimary} is true (called from the IC event listener) the
 * player is always processed and marked as handled.
 * When {@code icPrimary} is false (called from the fallback Bukkit listener)
 * the player is skipped if already marked.
 */
public class TriggerProcessor {

    private static final String TRIGGER_ITEM        = "[item]";
    private static final String TRIGGER_INV         = "[inv]";
    private static final String TRIGGER_INVENTORY   = "[inventory]";
    private static final String TRIGGER_ENDERCHEST  = "[enderchest]";
    private static final String TRIGGER_EC          = "[ec]";
    private static final String TRIGGER_ARMOR       = "[armor]";
    private static final String TRIGGER_OFFHAND     = "[offhand]";
    private static final String TRIGGER_MAP         = "[map]";
    private static final String TRIGGER_BOOK        = "[book]";

    private final CubeTaleICAddon   addon;
    private final DiscordCHATBridge bridge;

    private final Set<UUID> recentlyProcessed = ConcurrentHashMap.newKeySet();

    public TriggerProcessor(CubeTaleICAddon addon, DiscordCHATBridge bridge) {
        this.addon  = addon;
        this.bridge = bridge;
    }

    /**
     * Processes the raw chat message and dispatches Discord renders.
     *
     * @param player     the player who sent the message
     * @param message    the raw message string
     * @param icPrimary  true when called from the authoritative IC event path
     */
    public void process(Player player, String message, boolean icPrimary) {
        if (!bridge.isReady()) return;

        String lower = message.toLowerCase();
        AddonConfig cfg = addon.getAddonConfig();

        boolean hasItem       = cfg.isTriggerItem()       && lower.contains(TRIGGER_ITEM);
        boolean hasInv        = cfg.isTriggerInventory()  && (lower.contains(TRIGGER_INV) || lower.contains(TRIGGER_INVENTORY));
        boolean hasEnderchest = cfg.isTriggerEnderchest() && (lower.contains(TRIGGER_ENDERCHEST) || lower.contains(TRIGGER_EC));
        boolean hasArmor      = cfg.isTriggerArmor()      && lower.contains(TRIGGER_ARMOR);
        boolean hasOffhand    = cfg.isTriggerOffhand()    && lower.contains(TRIGGER_OFFHAND);
        boolean hasMap        = cfg.isTriggerMap()        && lower.contains(TRIGGER_MAP);
        boolean hasBook       = cfg.isTriggerBook()       && lower.contains(TRIGGER_BOOK);

        boolean anyTrigger = hasItem || hasInv || hasEnderchest || hasArmor || hasOffhand || hasMap || hasBook;
        if (!anyTrigger) return;

        UUID uid = player.getUniqueId();

        if (!icPrimary && recentlyProcessed.contains(uid)) {
            if (cfg.isDebug()) addon.getLogger().info("[DEBUG] Skipping duplicate (already processed by IC event).");
            return;
        }

        markProcessed(uid);

        final boolean fItem       = hasItem;
        final boolean fInv        = hasInv;
        final boolean fEnderchest = hasEnderchest;
        final boolean fArmor      = hasArmor;
        final boolean fOffhand    = hasOffhand;
        final boolean fMap        = hasMap;
        final boolean fBook       = hasBook;

        addon.getServer().getScheduler().runTask(addon, () -> {
            if (!player.isOnline()) return;

            // ── [item] ──────────────────────────────────────────────────────────
            if (fItem) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() != Material.AIR) {
                    final ItemStack copy = held.clone();
                    addon.getServer().getScheduler().runTaskAsynchronously(addon, () ->
                            bridge.sendItemDisplay(player, copy));
                }
            }

            // ── [inv] ───────────────────────────────────────────────────────────
            if (fInv) {
                ItemStack[] full  = new ItemStack[41];
                ItemStack[] main  = player.getInventory().getContents();
                ItemStack[] armor = player.getInventory().getArmorContents();
                ItemStack[] extra = player.getInventory().getExtraContents();
                System.arraycopy(main, 0, full, 0, Math.min(main.length, 36));
                if (armor != null)
                    for (int i = 0; i < Math.min(armor.length, 4); i++) full[36 + i] = armor[i];
                if (extra != null && extra.length > 0) full[40] = extra[0];
                final ItemStack[] snapshot = full;
                addon.getServer().getScheduler().runTaskAsynchronously(addon, () ->
                        bridge.sendInventory(player, snapshot));
            }

            // ── [enderchest] / [ec] ─────────────────────────────────────────────
            if (fEnderchest) {
                final ItemStack[] ec = player.getEnderChest().getContents().clone();
                addon.getServer().getScheduler().runTaskAsynchronously(addon, () ->
                        bridge.sendEnderChest(player, ec));
            }

            // ── [armor] ─────────────────────────────────────────────────────────
            if (fArmor) {
                final ItemStack[] armorSlots = player.getInventory().getArmorContents().clone();
                final ItemStack   offhand    = player.getInventory().getItemInOffHand().clone();
                addon.getServer().getScheduler().runTaskAsynchronously(addon, () ->
                        bridge.sendArmor(player, armorSlots, offhand));
            }

            // ── [offhand] ───────────────────────────────────────────────────────
            if (fOffhand) {
                ItemStack offhand = player.getInventory().getItemInOffHand();
                if (offhand.getType() != Material.AIR) {
                    final ItemStack copy = offhand.clone();
                    addon.getServer().getScheduler().runTaskAsynchronously(addon, () ->
                            bridge.sendOffhand(player, copy));
                }
            }

            // ── [map] ───────────────────────────────────────────────────────────
            if (fMap) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() == Material.FILLED_MAP || held.getType() == Material.MAP) {
                    final ItemStack copy = held.clone();
                    addon.getServer().getScheduler().runTaskAsynchronously(addon, () ->
                            bridge.sendMap(player, copy));
                }
            }

            // ── [book] ──────────────────────────────────────────────────────────
            if (fBook) {
                ItemStack held = player.getInventory().getItemInMainHand();
                if (held.getType() == Material.WRITTEN_BOOK || held.getType() == Material.WRITABLE_BOOK) {
                    final ItemStack copy = held.clone();
                    addon.getServer().getScheduler().runTaskAsynchronously(addon, () ->
                            bridge.sendBook(player, copy));
                }
            }
        });
    }

    private void markProcessed(UUID uid) {
        recentlyProcessed.add(uid);
        addon.getServer().getScheduler().runTaskLaterAsynchronously(addon, () ->
                recentlyProcessed.remove(uid), 10L);
    }
}
