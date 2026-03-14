package com.cubetale.icaddon.config;

import com.cubetale.icaddon.CubeTaleICAddon;

/**
 * Typed wrapper around the addon's config.yml values.
 */
public class AddonConfig {

    private final CubeTaleICAddon plugin;

    private boolean triggerItem;
    private boolean triggerInventory;
    private boolean triggerEnderchest;
    private boolean triggerArmor;
    private boolean triggerOffhand;
    private boolean triggerMap;
    private boolean triggerBook;
    private boolean stripTriggersFromChat;
    private boolean debug;

    public AddonConfig(CubeTaleICAddon plugin) {
        this.plugin = plugin;
        load();
    }

    public void reload() {
        load();
    }

    private void load() {
        triggerItem         = plugin.getConfig().getBoolean("triggers.item",         true);
        triggerInventory    = plugin.getConfig().getBoolean("triggers.inventory",    true);
        triggerEnderchest   = plugin.getConfig().getBoolean("triggers.enderchest",   true);
        triggerArmor        = plugin.getConfig().getBoolean("triggers.armor",        true);
        triggerOffhand      = plugin.getConfig().getBoolean("triggers.offhand",      true);
        triggerMap          = plugin.getConfig().getBoolean("triggers.map",          true);
        triggerBook         = plugin.getConfig().getBoolean("triggers.book",         true);
        stripTriggersFromChat = plugin.getConfig().getBoolean("strip-triggers-from-chat", true);
        debug               = plugin.getConfig().getBoolean("debug",                 false);
    }

    public boolean isTriggerItem()         { return triggerItem; }
    public boolean isTriggerInventory()    { return triggerInventory; }
    public boolean isTriggerEnderchest()   { return triggerEnderchest; }
    public boolean isTriggerArmor()        { return triggerArmor; }
    public boolean isTriggerOffhand()      { return triggerOffhand; }
    public boolean isTriggerMap()          { return triggerMap; }
    public boolean isTriggerBook()         { return triggerBook; }
    public boolean isStripTriggersFromChat() { return stripTriggersFromChat; }
    public boolean isDebug()               { return debug; }
}
