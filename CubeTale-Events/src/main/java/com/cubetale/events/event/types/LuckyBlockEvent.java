package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class LuckyBlockEvent extends GameEvent {

    private final List<Location> luckyBlocks = new ArrayList<>();
    private final Random random = new Random();

    public LuckyBlockEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.LUCKY_BLOCK);
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        var cfg = plugin.getEventsConfig().get();

        String matName = cfg.getString("events.lucky_block.block-material", "GOLD_BLOCK");
        Material mat;
        try { mat = Material.valueOf(matName.toUpperCase()); }
        catch (Exception e) { mat = Material.GOLD_BLOCK; }
        final Material blockMat = mat;

        int count        = cfg.getInt("events.lucky_block.block-count", 30);
        int radius       = cfg.getInt("events.lucky_block.scatter-radius", 100);
        int minY         = cfg.getInt("events.lucky_block.min-y", 60);
        int maxY         = cfg.getInt("events.lucky_block.max-y", 120);
        String worldName = cfg.getString("events.lucky_block.world", "");

        World world = worldName.isEmpty()
                ? Bukkit.getWorlds().get(0)
                : Bukkit.getWorld(worldName);
        if (world == null) return;

        Location spawn = world.getSpawnLocation();
        for (int i = 0; i < count; i++) {
            int x = (int)(spawn.getX() + (random.nextDouble() * radius * 2) - radius);
            int z = (int)(spawn.getZ() + (random.nextDouble() * radius * 2) - radius);
            int y = minY + random.nextInt(Math.max(1, maxY - minY));
            Block block = world.getBlockAt(x, y, z);
            if (block.getType() == Material.AIR) {
                block.setType(blockMat);
                luckyBlocks.add(block.getLocation());
            }
        }

        Bukkit.broadcastMessage(MessageUtil.colorize("&#FFD700&l" + luckyBlocks.size()
                + " Lucky Blocks &fhave been hidden! Break them to reveal your fate!"));

        int duration = getDurationSeconds();
        taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            cleanup(blockMat);
            plugin.getEventManager().endCurrentEvent(false);
        }, duration * 20L);
    }

    /**
     * Called from EventListener when a player breaks a block.
     * args[0] = Block broken
     */
    @Override
    public void onPlayerAction(Player player, Object... args) {
        if (!isParticipant(player)) return;
        if (args.length < 1 || !(args[0] instanceof Block broken)) return;

        Location loc = broken.getLocation();
        if (!luckyBlocks.remove(loc)) return;

        addScore(player, 1);
        triggerLucky(player, loc);
    }

    private void triggerLucky(Player player, Location loc) {
        int roll = random.nextInt(100);
        World w = loc.getWorld();
        if (w == null) return;

        if (roll < 5) {
            // Jackpot — diamonds
            w.dropItemNaturally(loc, new ItemStack(Material.DIAMOND, 5 + random.nextInt(10)));
            w.dropItemNaturally(loc, new ItemStack(Material.EMERALD, 3));
            player.sendMessage(MessageUtil.colorize("&#FFD700★ JACKPOT! You found the mother lode!"));
            w.playEffect(loc, Effect.ENDER_SIGNAL, 0);
        } else if (roll < 20) {
            // Good — useful items
            w.dropItemNaturally(loc, new ItemStack(Material.GOLDEN_APPLE, 2));
            w.dropItemNaturally(loc, new ItemStack(Material.IRON_INGOT, 10));
            player.sendMessage(MessageUtil.colorize("&a★ Lucky! You found useful items!"));
        } else if (roll < 40) {
            // Neutral — XP
            player.giveExp(50 + random.nextInt(100));
            player.sendMessage(MessageUtil.colorize("&e★ You gained experience!"));
        } else if (roll < 60) {
            // Bad — spawn hostile mob
            w.spawnEntity(loc.add(0, 1, 0), EntityType.ZOMBIE);
            w.spawnEntity(loc.add(0, 1, 0), EntityType.ZOMBIE);
            player.sendMessage(MessageUtil.colorize("&c☠ Unlucky! Zombies appeared!"));
        } else if (roll < 75) {
            // Bad — lightning
            w.strikeLightningEffect(loc.add(0, 1, 0));
            player.sendMessage(MessageUtil.colorize("&c⚡ ZAPPED!"));
        } else {
            // Normal — random loot
            Material[] loot = {Material.COAL, Material.BONE, Material.ARROW, Material.BREAD, Material.STICK};
            w.dropItemNaturally(loc, new ItemStack(loot[random.nextInt(loot.length)], 4 + random.nextInt(8)));
            player.sendMessage(MessageUtil.colorize("&7★ Meh. Just some junk."));
        }
    }

    private void cleanup(Material mat) {
        for (Location loc : luckyBlocks) {
            Block b = loc.getBlock();
            if (b.getType() == mat) b.setType(Material.AIR);
        }
        luckyBlocks.clear();
    }

    @Override
    public void end(boolean forced) {
        cancelTask();
        var cfg = plugin.getEventsConfig().get();
        String matName = cfg.getString("events.lucky_block.block-material", "GOLD_BLOCK");
        try { cleanup(Material.valueOf(matName.toUpperCase())); } catch (Exception ignored) {}
    }
}
