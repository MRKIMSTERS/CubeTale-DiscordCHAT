package com.cubetale.discordchat.sync;

import com.cubetale.discordchat.CubeTaleDiscordChat;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maps Discord role IDs to Minecraft permission group names and vice versa.
 * Used by {@link RoleSyncManager} to determine which groups to assign.
 */
public class GroupMapper {

    private final CubeTaleDiscordChat plugin;
    private final Map<String, String> roleToGroup = new HashMap<>();
    private final Map<String, String> groupToRole = new HashMap<>();

    public GroupMapper(CubeTaleDiscordChat plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * (Re)load mappings from config.
     */
    public void load() {
        roleToGroup.clear();
        groupToRole.clear();

        ConfigurationSection mappings = plugin.getConfig().getConfigurationSection("role-sync.mappings");
        if (mappings == null) {
            plugin.getPluginLogger().debug("No role-sync.mappings section found in config.");
            return;
        }

        for (String roleId : mappings.getKeys(false)) {
            String groupName = mappings.getString(roleId);
            if (groupName == null || groupName.isBlank()) continue;
            roleToGroup.put(roleId, groupName);
            groupToRole.put(groupName, roleId);
        }

        plugin.getPluginLogger().debug("Loaded " + roleToGroup.size() + " role-to-group mapping(s).");
    }

    /**
     * Returns the Minecraft group name for a Discord role ID, or {@code null} if not mapped.
     */
    public String getGroupForRole(String discordRoleId) {
        return roleToGroup.get(discordRoleId);
    }

    /**
     * Returns the Discord role ID for a Minecraft group name, or {@code null} if not mapped.
     */
    public String getRoleForGroup(String groupName) {
        return groupToRole.get(groupName);
    }

    /**
     * Returns all configured Discord role ID → Minecraft group mappings (unmodifiable).
     */
    public Map<String, String> getAllMappings() {
        return Collections.unmodifiableMap(roleToGroup);
    }

    /**
     * Returns all configured Discord role IDs that have a mapping.
     */
    public Set<String> getMappedRoleIds() {
        return Collections.unmodifiableSet(roleToGroup.keySet());
    }

    /**
     * Returns {@code true} if the given Discord role ID has a mapping.
     */
    public boolean hasMappingForRole(String discordRoleId) {
        return roleToGroup.containsKey(discordRoleId);
    }

    /**
     * Returns {@code true} if the given Minecraft group name has a mapping.
     */
    public boolean hasMappingForGroup(String groupName) {
        return groupToRole.containsKey(groupName);
    }
}
