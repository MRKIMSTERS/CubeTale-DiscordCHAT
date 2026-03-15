package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.util.MessageUtil;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ScavengerHuntEvent extends GameEvent {

    private final List<Location> hiddenItems = new ArrayList<>();
    private final List<Material> targets = new ArrayList<>();
    private final Random random = new Random();

    public ScavengerHuntEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.SCAVENGER_HUNT);
    }

    @Override
    public void start() {
        startedAt = System.currentTimeMillis();
        var cfg = plugin.getEventsConfig().get();

        List<String> possible = cfg.getStringList("events.scavenger_hunt.possible-items");
        int count     = cfg.getInt("events.scavenger_hunt.item-count", 5);
        int radius    = cfg.getInt("events.scavenger_hunt.scatter-radius", 200);
        String wName  = cfg.getString("events.scavenger_hunt.world", "");

        World world = wName.isEmpty() ? Bukkit.getWorlds().get(0) : Bukkit.getWorld(wName);
        if (world == null) return;

        List<Material> pool = new ArrayList<>();
        for (String s : possible) { try { pool.add(Material.valueOf(s.toUpperCase())); } catch (Exception ignored) {} }
        Collections.shuffle(pool);

        Location spawn = world.getSpawnLocation();
        for (int i = 0; i < Math.min(count, pool.size()); i++) {
            Material mat = pool.get(i);
            targets.add(mat);
            int x = (int)(spawn.getX() + (random.nextDouble() * radius * 2) - radius);
            int z = (int)(spawn.getZ() + (random.nextDouble() * radius * 2) - radius);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location loc = new Location(world, x, y, z);
            world.getBlockAt(loc).setType(mat == Material.DIAMOND ? Material.DIAMOND_BLOCK : mat);
            hiddenItems.add(loc);
        }

        // Announce clue-style hints
        StringBuilder sb = new StringBuilder("&#5865F2&lFind these items: ");
        for (Material m : targets) sb.append("&f").append(m.name()).append(" ");
        Bukkit.broadcastMessage(MessageUtil.colorize(sb.toString()));
        Bukkit.broadcastMessage(MessageUtil.colorize("&7Hint: Items are within " + radius + " blocks of spawn!"));

        int duration = getDurationSeconds();
        taskId = Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            cleanup();
            plugin.getEventManager().endCurrentEvent(false);
        }, duration * 20L);
    }

    /** args[0] = Block broken */
    @Override
    public void onPlayerAction(Player player, Object... args) {
        if (!isParticipant(player)) return;
        if (args.length < 1 || !(args[0] instanceof Block broken)) return;
        if (!hiddenItems.remove(broken.getLocation())) return;

        addScore(player, 1);
        player.sendMessage(MessageUtil.colorize("&#5865F2★ You found a hidden item! Score: &f" + getScore(player)));
        Bukkit.broadcastMessage(MessageUtil.colorize("&#5865F2" + player.getName() + " &ffound a hidden item! &7("
                + hiddenItems.size() + " remaining)"));

        if (hiddenItems.isEmpty()) {
            cancelTask();
            plugin.getEventManager().endCurrentEvent(false);
        }
    }

    private void cleanup() {
        for (Location loc : hiddenItems) {
            Block b = loc.getBlock();
            if (targets.stream().anyMatch(m -> m.name().equals(b.getType().name())
                    || (b.getType() == Material.DIAMOND_BLOCK))) {
                b.setType(Material.AIR);
            }
        }
        hiddenItems.clear();
    }

    @Override
    public void end(boolean forced) {
        cancelTask();
        cleanup();
    }
}
