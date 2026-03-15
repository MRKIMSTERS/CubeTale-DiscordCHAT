package com.cubetale.events.event.types;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class DropPartyEvent extends GameEvent {

    private final Random random = new Random();

    public DropPartyEvent(CubeTaleEvents plugin) {
        super(plugin, EventType.DROP_PARTY);
    }

    @Override
    public void start() {
        int duration = getDurationSeconds();
        FileConfiguration cfg = plugin.getEventsConfig().get();
        int itemCount  = cfg.getInt("events.drop_party.item-count", 80);
        int radius     = cfg.getInt("events.drop_party.drop-radius", 15);
        String locStr  = cfg.getString("events.drop_party.location", "");
        Location center = parseLocation(locStr);

        if (center == null) {
            // Fall back to first participant's location
            if (!participants.isEmpty()) {
                center = Objects.requireNonNull(Bukkit.getPlayer(participants.iterator().next())).getLocation();
            } else {
                plugin.getLogger().warning("[DropParty] No location set and no participants found.");
                return;
            }
        }

        final Location dropCenter = center;
        final int dropRadius      = radius;
        final List<String> pool   = buildPool(cfg);

        startedAt = System.currentTimeMillis();

        // Spread drops evenly over the duration
        int dropInterval = Math.max(1, (duration * 20) / itemCount);
        final int[] dropsLeft = {itemCount};

        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            if (dropsLeft[0] <= 0) {
                cancelTask();
                plugin.getEventManager().endCurrentEvent(false);
                return;
            }
            dropRandomItem(dropCenter, dropRadius, pool);
            dropsLeft[0]--;
        }, 20L, dropInterval);
    }

    @Override
    public void end(boolean forced) {
        cancelTask();
    }

    private void dropRandomItem(Location center, int radius, List<String> pool) {
        if (pool.isEmpty()) return;
        String pick = pool.get(random.nextInt(pool.size()));
        Material mat;
        int amount = 1;
        try {
            String[] parts = pick.split(":");
            mat    = Material.valueOf(parts[0].toUpperCase());
            if (parts.length > 1) amount = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return;
        }

        World world = center.getWorld();
        if (world == null) return;
        double x = center.getX() + (random.nextDouble() * radius * 2) - radius;
        double z = center.getZ() + (random.nextDouble() * radius * 2) - radius;
        double y = center.getY() + 10;
        Location dropLoc = new Location(world, x, y, z);

        Item item = world.dropItem(dropLoc, new ItemStack(mat, amount));
        item.setVelocity(item.getVelocity().multiply(0));
    }

    private List<String> buildPool(FileConfiguration cfg) {
        List<String> pool = new ArrayList<>();
        var section = cfg.getConfigurationSection("events.drop_party.item-pool");
        if (section == null) return pool;
        for (String key : section.getKeys(false)) {
            int weight = section.getInt(key, 1);
            for (int i = 0; i < weight; i++) pool.add(key);
        }
        Collections.shuffle(pool);
        return pool;
    }

    private Location parseLocation(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] p = s.split(",");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]));
        } catch (Exception e) { return null; }
    }
}
