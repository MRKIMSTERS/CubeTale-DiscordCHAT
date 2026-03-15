# CubeTale Minecraft Plugin Workspace

## Overview

Three Maven/Java Minecraft plugins (Spigot/Paper 1.21+). Two bridge in-game
chat with Discord, and one provides a full automated events system.

| Plugin | Directory | Output JAR |
|--------|-----------|------------|
| CubeTale-DiscordCHAT | `CubeTale-DiscordCHAT/` | `target/CubeTale-DiscordCHAT-1.0.0.jar` (fat JAR ~34 MB) |
| CubeTale-InteractiveChat-Addon | `CubeTale-InteractiveChat-Addon/` | `target/CubeTale-InteractiveChat-Addon-1.0.0.jar` (~18 KB) |
| CubeTale-Events | `CubeTale-Events/` | `target/CubeTale-Events-1.0.0.jar` (~14 MB) |

**Build all plugins** (Run button / "Start application" workflow):
```
cd CubeTale-DiscordCHAT && mvn package -B -q
cd CubeTale-InteractiveChat-Addon && mvn package -B -q
cd CubeTale-Events && mvn package -B -q
```

---

## Build Environment

- **Java**: GraalVM CE 22.3 (Java 19) — targets Java 17 bytecode
- **Maven**: 3.8.6
- **Spigot API**: 1.21-R0.1-SNAPSHOT (Java 17 class files — `compile` scope to pull transitive deps)
- **Paper API**: NOT used at compile time — its 1.21 snapshot was compiled with Java 21 (class version 65) which is incompatible here. The server provides Paper at runtime; we compile against Spigot API only.

---

## CubeTale-DiscordCHAT

Main plugin. Provides the Discord bridge, webhook infrastructure, and image rendering utilities that the addon consumes via reflection.

### Features
- Bidirectional chat sync (Minecraft ↔ Discord via webhooks with player-skin avatars)
- Account linking (`/link`, `/unlink`, `/linked`)
- Role sync via LuckPerms
- Console channel (live feed + execute commands from Discord)
- Event notifications (join/leave, death, advancements, server start/stop)
- `[item]` / `[inv]` rendering — sends Minecraft-style tooltip images to Discord
- Auto-updating server status channel embed (player count, TPS, RAM, uptime)
- Discord slash commands: `/players`, `/status`, `/execute`, `/link`, `/dm`, `/profile`
- PlaceholderAPI expansion
- SQLite/MySQL storage via HikariCP

### Key dependencies
- JDA 5 (shaded, relocated to `com.cubetale.discordchat.libs.jda`)
- discord-webhooks 0.8.4
- HikariCP 5 + SQLite JDBC
- Spigot API 1.21 + Paper API 1.21 (both `provided`)

### Package structure
```
com.cubetale.discordchat/
├── CubeTaleDiscordChat.java
├── config/          # ConfigManager, MessagesConfig
├── discord/         # DiscordBot, DiscordListener, SlashCommandManager, WebhookManager
├── minecraft/       # MinecraftListener, ChatHandler, CommandManager
├── linking/         # LinkManager, LinkCommand, VerificationManager
├── sync/            # RoleSyncManager, GroupMapper
├── console/         # ConsoleManager
├── database/        # DatabaseManager (SQLite + MySQL)
├── placeholders/    # DiscordPlaceholderExpansion (PAPI)
└── util/            # AvatarUrlBuilder, ColorConverter, MessageFormatter,
                     # MinecraftImageRenderer (renderItem, renderInventory,
                     #   renderEnderChest, renderArmor, renderMap, renderBook)
```

---

## CubeTale-InteractiveChat-Addon

Addon plugin. Hooks into InteractiveChat (LOOHP) events to intercept
`[item]`, `[inv]`, `[enderchest]`, `[armor]`, `[offhand]`, `[map]`, `[book]`
triggers and forwards rendered images to Discord via CubeTale-DiscordCHAT's
webhook system — all via reflection (no compile-time dependency on either
CubeTale-DiscordCHAT or InteractiveChat).

### Design
- `plugin.yml` declares `depend: [InteractiveChat, CubeTale-DiscordCHAT]`
- `ICEventListener` hooks IC's `PrePacketComponentProcessEvent` via reflection
- `ChatListener` is a Bukkit-level `AsyncPlayerChatEvent` fallback
- `TriggerProcessor` de-duplicates triggers per-player over a 10-tick window
- `DiscordCHATBridge` locates CubeTale-DiscordCHAT's classes and calls
  `WebhookManager` / `MinecraftImageRenderer` via reflection at runtime

### Package structure
```
com.cubetale.icaddon/
├── CubeTaleICAddon.java      # Main plugin class
├── config/                   # AddonConfig
├── hook/                     # ICEventListener, ChatListener, DiscordCHATBridge
└── render/                   # TriggerProcessor
```

---

## CI / GitHub Actions

Both plugins ship a `.github/workflows/build.yml` that runs `mvn package`
on push/PR to produce release-ready JARs as build artifacts.

---

## CubeTale-Events

Automated and manual in-game events system with Discord integration, leaderboards, voting, and 10 different event types.

### Soft Dependencies
- `CubeTale-DiscordCHAT` — posts event start/end/winner embeds to a Discord channel via reflection
- `PlaceholderAPI` — exposes `%cubetaleevents_*%` placeholders

### Features
- **10 Event Types**: Drop Party, Trivia, Mob Hunt, Ore Hunt, Fishing Competition, Spleef, PvP Tournament, Lucky Block, Scavenger Hunt, TNT Run
- **Automated Scheduler**: fires events on a configurable interval with blackout hours, minimum player count, and pre-announce warnings
- **Vote System**: players vote for the next event type before it starts (`/eventvote`)
- **Rewards**: 1st / 2nd / 3rd place + participation rewards via console commands (compatible with EssentialsX economy, etc.)
- **Leaderboard**: SQLite or MySQL storage; `/event top` and `/event stats`
- **Discord Bridge**: posts embeds to a dedicated Discord events channel via CubeTale-DiscordCHAT
- **PlaceholderAPI expansion**: `%cubetaleevents_current_event%`, `%cubetaleevents_wins_total%`, etc.
- **Full command set**: `/event join|leave|spectate|info|types|top|stats|start|stop|setlocation|announce|reload`

### Package structure
```
com.cubetale.events/
├── CubeTaleEvents.java         # Main plugin class
├── config/                     # EventsConfig
├── event/                      # GameEvent (abstract), EventManager, EventScheduler, EventType, EventState
│   └── types/                  # DropPartyEvent, TriviaEvent, MobHuntEvent, OreHuntEvent,
│                               # FishingCompetitionEvent, SpleefEvent, PvpTournamentEvent,
│                               # LuckyBlockEvent, ScavengerHuntEvent, TntRunEvent
├── reward/                     # RewardManager
├── leaderboard/                # LeaderboardManager (HikariCP + SQLite/MySQL)
├── vote/                       # VoteManager
├── discord/                    # DiscordBridge (reflection-based hook into CubeTale-DiscordCHAT)
├── command/                    # EventCommand, VoteCommand
├── listener/                   # EventListener
├── placeholder/                # EventsPlaceholderExpansion
└── util/                       # MessageUtil (hex color + message file support)
```

### Config files
- `config.yml` — all settings (scheduler, event types, rewards, Discord, leaderboard)
- `messages.yml` — all player-facing messages (supports hex colors)
- `trivia-questions.yml` — trivia question bank (easily extendable)

### Admin setup
1. Drop `CubeTale-Events-1.0.0.jar` in your server's `plugins/` folder
2. Start the server, then edit `plugins/CubeTale-Events/config.yml`
3. Set arena/drop locations with `/event setlocation <name>` in-game
4. Set `discord.channel-id` to your events Discord channel ID
5. Adjust `scheduler.interval-minutes` for how often auto-events fire
