# CubeTale Minecraft Plugin Workspace

## Overview

Two Maven/Java Minecraft plugins (Spigot/Paper 1.21+) that together bridge
in-game chat with Discord — no DiscordSRV required.

| Plugin | Directory | Output JAR |
|--------|-----------|------------|
| CubeTale-DiscordCHAT | `CubeTale-DiscordCHAT/` | `target/CubeTale-DiscordCHAT-1.0.0.jar` (fat JAR ~34 MB) |
| CubeTale-InteractiveChat-Addon | `CubeTale-InteractiveChat-Addon/` | `target/CubeTale-InteractiveChat-Addon-1.0.0.jar` (~18 KB) |

**Build both plugins** (Run button / "Start application" workflow):
```
cd CubeTale-DiscordCHAT && mvn package -B -q
cd CubeTale-InteractiveChat-Addon && mvn package -B -q
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
