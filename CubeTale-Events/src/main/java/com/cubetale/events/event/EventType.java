package com.cubetale.events.event;

public enum EventType {
    DROP_PARTY("Drop Party", "drop_party"),
    TRIVIA("Trivia", "trivia"),
    MOB_HUNT("Mob Hunt", "mob_hunt"),
    ORE_HUNT("Ore Hunt", "ore_hunt"),
    FISHING_COMPETITION("Fishing Competition", "fishing_competition"),
    SPLEEF("Spleef", "spleef"),
    PVP_TOURNAMENT("PvP Tournament", "pvp_tournament"),
    LUCKY_BLOCK("Lucky Block", "lucky_block"),
    SCAVENGER_HUNT("Scavenger Hunt", "scavenger_hunt"),
    TNT_RUN("TNT Run", "tnt_run");

    private final String displayName;
    private final String configKey;

    EventType(String displayName, String configKey) {
        this.displayName = displayName;
        this.configKey = configKey;
    }

    public String getDisplayName() { return displayName; }
    public String getConfigKey() { return configKey; }

    public static EventType fromString(String s) {
        if (s == null) return null;
        for (EventType t : values()) {
            if (t.name().equalsIgnoreCase(s)
                    || t.displayName.equalsIgnoreCase(s)
                    || t.configKey.equalsIgnoreCase(s)) {
                return t;
            }
        }
        return null;
    }
}
