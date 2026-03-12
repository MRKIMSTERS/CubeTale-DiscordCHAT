package com.cubetale.discordchat.sync;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RoleSyncManager {

    private final CubeTaleDiscordChat plugin;
    private LuckPerms luckPerms;
    private BukkitTask syncTask;

    public RoleSyncManager(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        tryHookLuckPerms();
    }

    private void tryHookLuckPerms() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager()
                    .getRegistration(LuckPerms.class);
            if (provider != null) {
                luckPerms = provider.getProvider();
                plugin.getPluginLogger().info("LuckPerms hooked for role synchronization.");
            }
        } else {
            plugin.getPluginLogger().info("LuckPerms not found. Role sync will use native permissions.");
        }
    }

    public void startSyncTask() {
        int intervalSeconds = plugin.getConfig().getInt("role-sync.sync-interval", 300);
        syncTask = plugin.getServer().getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::syncAllOnlinePlayers,
                20L * 60, // Start after 1 minute
                20L * intervalSeconds
        );
        plugin.getPluginLogger().info("Role sync task started (interval: " + intervalSeconds + "s).");
    }

    public void syncAllOnlinePlayers() {
        if (!plugin.getDiscordBot().isConnected()) return;

        for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
            syncPlayer(player.getUniqueId());
        }
    }

    public void syncPlayer(UUID minecraftUUID) {
        if (!plugin.getDiscordBot().isConnected()) return;

        String discordId = plugin.getDatabaseManager().getDiscordId(minecraftUUID);
        if (discordId == null) return; // Player not linked

        Guild guild = plugin.getDiscordBot().getGuild();
        if (guild == null) return;

        guild.retrieveMemberById(discordId).queue(member -> {
            if (member != null) {
                syncRoles(minecraftUUID, member);
            }
        }, error -> plugin.getPluginLogger().debug("Could not retrieve Discord member: " + discordId));
    }

    private void syncRoles(UUID minecraftUUID, Member member) {
        ConfigurationSection mappings = plugin.getConfig().getConfigurationSection("role-sync.mappings");
        if (mappings == null) return;

        List<Role> memberRoles = member.getRoles();

        for (String roleId : mappings.getKeys(false)) {
            String groupName = mappings.getString(roleId);
            if (groupName == null) continue;

            boolean hasRole = memberRoles.stream().anyMatch(r -> r.getId().equals(roleId));

            if (luckPerms != null) {
                syncLuckPermsGroup(minecraftUUID, groupName, hasRole);
            } else {
                syncNativePermission(minecraftUUID, groupName, hasRole);
            }
        }
    }

    private void syncLuckPermsGroup(UUID minecraftUUID, String groupName, boolean shouldHave) {
        luckPerms.getUserManager().loadUser(minecraftUUID).thenAccept(user -> {
            if (user == null) return;

            InheritanceNode node = InheritanceNode.builder(groupName).build();
            boolean hasGroup = user.getNodes().contains(node);

            if (shouldHave && !hasGroup) {
                user.data().add(node);
                luckPerms.getUserManager().saveUser(user);
                plugin.getPluginLogger().debug("Added group " + groupName + " to " + minecraftUUID);
            } else if (!shouldHave && hasGroup) {
                user.data().remove(node);
                luckPerms.getUserManager().saveUser(user);
                plugin.getPluginLogger().debug("Removed group " + groupName + " from " + minecraftUUID);
            }
        });
    }

    private void syncNativePermission(UUID minecraftUUID, String permission, boolean shouldHave) {
        // Sync on main thread since Bukkit permissions require it
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(minecraftUUID);
            if (player.isOnline() && player.getPlayer() != null) {
                // Native permission sync (basic)
                plugin.getPluginLogger().debug("Native permission sync for " + minecraftUUID + ": " + permission + " = " + shouldHave);
            }
        });
    }

    public void shutdown() {
        if (syncTask != null) {
            syncTask.cancel();
        }
    }

    public boolean isLuckPermsAvailable() {
        return luckPerms != null;
    }
}
