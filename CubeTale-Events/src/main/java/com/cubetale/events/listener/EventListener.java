package com.cubetale.events.listener;

import com.cubetale.events.CubeTaleEvents;
import com.cubetale.events.event.EventState;
import com.cubetale.events.event.EventType;
import com.cubetale.events.event.GameEvent;
import com.cubetale.events.event.types.*;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class EventListener implements Listener {

    private final CubeTaleEvents plugin;

    public EventListener(CubeTaleEvents plugin) {
        this.plugin = plugin;
    }

    // ── Chat (Trivia answers / vote) ──────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String msg    = event.getMessage();
        GameEvent current = plugin.getEventManager().getCurrentEvent();
        if (current == null) return;

        // Trivia — intercept correct answers
        if (current instanceof TriviaEvent trivia && current.getState() == EventState.RUNNING) {
            if (trivia.isParticipant(player)) {
                // We call onPlayerAction from the async thread; TriviaEvent does its own sync scheduling
                trivia.onPlayerAction(player, msg);
            }
        }
    }

    // ── Block break ───────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block  block  = event.getBlock();
        GameEvent current = plugin.getEventManager().getCurrentEvent();
        if (current == null || current.getState() != EventState.RUNNING) return;

        if (current instanceof OreHuntEvent oreHunt) {
            if (block.getType() == oreHunt.getTargetOre()) {
                oreHunt.onPlayerAction(player, block.getType());
            }
        } else if (current instanceof LuckyBlockEvent luckyBlock) {
            luckyBlock.onPlayerAction(player, block);
        } else if (current instanceof ScavengerHuntEvent scavenger) {
            scavenger.onPlayerAction(player, block);
        } else if (current instanceof SpleefEvent spleef) {
            if (spleef.isParticipant(player)) spleef.onPlayerAction(player, block.getType());
        } else if (current instanceof TntRunEvent tntRun) {
            tntRun.onPlayerAction(player, block);
        }
    }

    // ── Entity death (mob hunt) ───────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        GameEvent current = plugin.getEventManager().getCurrentEvent();
        if (current == null || current.getState() != EventState.RUNNING) return;

        if (current instanceof MobHuntEvent mobHunt) {
            mobHunt.onPlayerAction(killer, entity.getType());
        }
    }

    // ── Player death (PvP Tournament / Spleef) ────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead   = event.getEntity();
        Player killer = dead.getKiller();
        GameEvent current = plugin.getEventManager().getCurrentEvent();
        if (current == null || current.getState() != EventState.RUNNING) return;

        if (current instanceof PvpTournamentEvent pvp) {
            pvp.onPlayerAction(dead, killer);
        }
    }

    // ── Fishing competition ───────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH
                && event.getState() != PlayerFishEvent.State.CAUGHT_ENTITY) return;

        GameEvent current = plugin.getEventManager().getCurrentEvent();
        if (current == null || current.getState() != EventState.RUNNING) return;

        boolean fishOnly = plugin.getEventsConfig().get().getBoolean("events.fishing_competition.fish-only", false);
        if (fishOnly && event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;

        if (current instanceof FishingCompetitionEvent fishing) {
            fishing.onPlayerAction(event.getPlayer());
        }
    }

    // ── Player quit ───────────────────────────────────────────────────────────

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        GameEvent current = plugin.getEventManager().getCurrentEvent();
        if (current == null) return;
        if (current.isParticipant(event.getPlayer())) {
            plugin.getEventManager().playerLeave(event.getPlayer());
        }
    }
}
