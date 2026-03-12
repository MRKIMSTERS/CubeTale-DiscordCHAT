# CubeTale-DiscordCHAT

A modern Minecraft ↔ Discord integration plugin for Spigot/Paper 1.21+.  
Bidirectional chat sync with **player skin avatars via webhooks**, account linking, role synchronization, and live console access — all in one self-contained plugin.

---

## Features

| Feature | Description |
|---|---|
| 🤖 **Built-in Discord Bot** | Self-hosted JDA bot — no external service needed |
| 💬 **Bidirectional Chat Sync** | Minecraft ↔ Discord messages in real time |
| 🖼️ **Player Avatars** | Chat messages appear in Discord with the player's Minecraft skin head |
| 🔗 **Account Linking** | Link Minecraft UUID to Discord ID via `/link` |
| 🎭 **Role Sync** | Sync Discord roles to LuckPerms groups automatically |
| 🖥️ **Console Channel** | Live server console feed + execute commands from Discord |
| 📣 **Event Notifications** | Join/leave, death, advancement, server start/stop embeds with player heads |
| ⚡ **Slash Commands** | `/players`, `/status`, `/execute`, `/link` in Discord |
| 🗃️ **Database** | SQLite (default) or MySQL with HikariCP connection pooling |
| 📋 **PlaceholderAPI** | Placeholders for linked status, Discord tag, bot status |

---

## Requirements

- **Java** 17+
- **Minecraft** 1.21+ (Spigot or Paper)
- **Optional:** LuckPerms (role sync), PlaceholderAPI (prefix/suffix in webhook username)

---

## Installation

1. Download the latest JAR from [Releases](../../releases).
2. Place it in your server's `plugins/` folder.
3. Start the server once to generate `config.yml`.
4. Edit `plugins/CubeTale-DiscordCHAT/config.yml` (see [Configuration](#configuration)).
5. Run `/ctd reload` or restart the server.

---

## Configuration

Open `plugins/CubeTale-DiscordCHAT/config.yml`:

```yaml
discord:
  token: "YOUR_BOT_TOKEN_HERE"       # From https://discord.com/developers/applications
  guild-id: "YOUR_GUILD_ID_HERE"     # Right-click server → Copy Server ID (Developer Mode)

  webhook:
    enabled: true
    url: "YOUR_WEBHOOK_URL_HERE"     # Channel Settings → Integrations → Webhooks → New Webhook
    avatar-service: "crafatar"       # crafatar | mcheads | cravatar | minotar

  channels:
    chat: "CHAT_CHANNEL_ID"          # Right-click channel → Copy Channel ID
    console: "CONSOLE_CHANNEL_ID"
```

### Getting a Bot Token

1. Go to [Discord Developer Portal](https://discord.com/developers/applications) → New Application.
2. Navigate to **Bot** → **Add Bot**.
3. Enable **Message Content Intent**, **Server Members Intent**, and **Presence Intent**.
4. Copy the token and paste it into `config.yml`.
5. Invite the bot to your server using the OAuth2 URL generator (scopes: `bot`, `applications.commands`; permissions: `Send Messages`, `Embed Links`, `Read Message History`).

### Getting a Webhook URL

1. Open your Discord channel settings → **Integrations** → **Webhooks** → **New Webhook**.
2. Copy the webhook URL and paste it as `discord.webhook.url` in `config.yml`.

> **Why a webhook?** Using a webhook (instead of a regular bot message) lets each player's message appear with their Minecraft skin avatar and their own username — exactly like they sent it themselves.

---

## Avatar Services

The plugin supports four skin-head URL providers:

| Key | URL format | Notes |
|---|---|---|
| `crafatar` | `https://crafatar.com/avatars/{uuid}?size=128&overlay` | Recommended; UUID-based |
| `mcheads` | `https://mc-heads.net/avatar/{uuid}/128` | Fast CDN |
| `cravatar` | `https://cravatar.eu/helmavatar/{name}/128.png` | Name-based |
| `minotar` | `https://minotar.net/helm/{name}/128.png` | Name-based |

---

## Commands

### Minecraft

| Command | Permission | Description |
|---|---|---|
| `/link` | `cubetale.link` | Start the Discord account linking process |
| `/unlink` | `cubetale.unlink` | Unlink your Discord account |
| `/linked [player]` | `cubetale.link` | Check link status |
| `/ctd reload` | `cubetale.admin` | Reload configuration |
| `/ctd status` | `cubetale.admin` | Show bot connection status |
| `/ctd debug` | `cubetale.admin` | Toggle debug logging |

### Discord (Slash Commands)

| Command | Description |
|---|---|
| `/players` | List online players |
| `/status` | Server status (TPS, uptime, player count) |
| `/execute <command>` | Run a console command (admin role required) |
| `/link [code]` | Link or start account linking |

---

## Account Linking

1. Player runs `/link` in Minecraft — a 6-character code is displayed.
2. Player runs `/link <code>` in Discord (or in the Minecraft chat with the provided instructions).
3. The plugin matches the code, stores the link in the database, and optionally syncs Discord roles.

---

## Role Synchronization

Enable in `config.yml`:

```yaml
role-sync:
  enabled: true
  sync-interval: 300   # seconds between automatic re-syncs
  mappings:
    "123456789012345678": "vip"    # Discord Role ID → LuckPerms group name
    "987654321098765432": "mvp"
```

Requires LuckPerms. Roles are synced on player join, on successful link, and periodically.

---

## PlaceholderAPI

| Placeholder | Value |
|---|---|
| `%cubetaledc_linked%` | `true` / `false` |
| `%cubetaledc_discord_tag%` | `Username#0000` or `Not Linked` |
| `%cubetaledc_discord_id%` | Discord user ID or `Not Linked` |
| `%cubetaledc_bot_status%` | `Online` / `Offline` |
| `%cubetaledc_bot_ping%` | Gateway latency in ms |
| `%cubetaledc_guild_name%` | Discord server name |

---

## Building from Source

Requires Java 17+ and Maven 3.8+.

```bash
git clone https://github.com/CubeTale/CubeTale-DiscordCHAT.git
cd CubeTale-DiscordCHAT/CubeTale-DiscordCHAT
mvn clean package
# Output: target/CubeTale-DiscordCHAT-1.0.0.jar
```

---

## Database

| Type | Default | Notes |
|---|---|---|
| SQLite | `database.db` | Default, zero config |
| MySQL | configurable | Recommended for high-traffic servers |

Switch by setting `database.type: MYSQL` and filling in the `database.mysql` block.

---

## License

MIT — see [LICENSE](LICENSE) for details.
