# Quick Start Guide

## Prerequisites

1. **Java 17** or higher installed
2. **Maven 3.6+** installed
3. **Discord Bot Token** from [Discord Developer Portal](https://discord.com/developers/applications)

## Setup Instructions

### 1. Build the Project

```bash
# Clone the repository (if not already done)
git clone https://github.com/Tanmayop9/discord-java-antinuke-.git
cd discord-java-antinuke-

# Build with Maven
mvn clean package
```

This will create `target/discord-antinuke-1.0.0.jar`

### 2. Configure the Bot

Edit `src/main/resources/config.json`:

```json
{
  "token": "YOUR_BOT_TOKEN_HERE",
  "ownerId": "YOUR_DISCORD_USER_ID",
  "prefix": "!"
}
```

**How to get your Discord User ID:**
1. Enable Developer Mode in Discord (Settings → Advanced → Developer Mode)
2. Right-click your name and select "Copy ID"

### 3. Bot Permissions

When inviting the bot, it needs these permissions:
- Administrator (or individually: Ban Members, Kick Members, Manage Channels, Manage Roles, Manage Webhooks, View Audit Log)

**Invite Link Generator:**
```
https://discord.com/api/oauth2/authorize?client_id=YOUR_BOT_CLIENT_ID&permissions=8&scope=bot%20applications.commands
```

Replace `YOUR_BOT_CLIENT_ID` with your bot's client ID from the Developer Portal.

### 4. Run the Bot

```bash
java -jar target/discord-antinuke-1.0.0.jar
```

Or with custom config path:
```bash
java -jar target/discord-antinuke-1.0.0.jar /path/to/config.json
```

### 5. Initial Setup in Discord

Once the bot is online:

1. Use `/antinuke` or `!antinuke` to open the dashboard
2. Click "👥 Whitelist" to add trusted users/roles
3. Configure protections with the select menu
4. Click "📸 Create Snapshot" to backup current server state

## Common Commands

### Slash Commands
- `/antinuke` - Open interactive dashboard
- `/whitelist add user @user` - Add user to whitelist
- `/whitelist add role @role` - Add role to whitelist
- `/snapshot` - Create server snapshot
- `/recover full` - Full server recovery

### Prefix Commands  
- `!antinuke` - Open dashboard
- `!whitelist add user @user` - Add user to whitelist
- `!snapshot` - Create snapshot
- `!recover full` - Full recovery
- `!help` - Show help

## Configuration Options

### Protection Types

All enabled by default:
- Anti-Ban
- Anti-Kick
- Anti-Channel Delete/Create
- Anti-Role Delete/Create
- Anti-Webhook
- Anti-Bot
- Anti-Raid
- Anti-Spam

### Thresholds

Customize in `config.json`:
```json
"thresholds": {
  "banThreshold": 3,           // Max bans in 60s
  "kickThreshold": 3,          // Max kicks in 60s
  "channelDeleteThreshold": 2, // Max channel deletes in 60s
  "raidJoinThreshold": 10      // Max joins in 10s
}
```

### Punishment Types

Choose one:
- `BAN` - Permanently ban malicious users (default)
- `KICK` - Kick malicious users
- `STRIP_ROLES` - Remove all roles from malicious users

### Fast Recovery Settings

```json
"fastRecovery": {
  "enabled": true,
  "concurrentOperations": 10,  // Parallel recovery threads
  "cacheSize": 10000,          // Cache capacity
  "snapshotInterval": 60       // Snapshot every 60 seconds
}
```

## Troubleshooting

### Bot doesn't respond
- Check if bot has Administrator permission
- Verify token is correct in config.json
- Check bot is online in Discord

### Antinuke not working
- Ensure protections are enabled in dashboard
- Check if user performing actions is whitelisted
- Verify bot has higher role than users it's monitoring

### Recovery not working
- Create a snapshot first using `!snapshot`
- Ensure fast recovery is enabled in config
- Check bot has permissions to create roles/channels

## Advanced Features

### Dual Monitoring
The bot uses both JDA events AND direct Discord API polling for maximum coverage. This is automatically enabled.

### Automatic Snapshots
Server state is automatically snapshotted every 60 seconds (configurable). Manual snapshots can be created anytime.

### Database Backups
Guild configurations are automatically backed up. Up to 10 backups are kept per guild.

### Multi-Server Support
The bot scales to handle 1000+ servers simultaneously with per-guild configurations.

## Support

For issues or questions:
1. Check the README.md for detailed documentation
2. Open an issue on GitHub
3. Review the logs in console for error messages

## Performance Tips

For servers with 1000+ members:
1. Increase `concurrentOperations` to 15-20
2. Set `cacheSize` to 20000+
3. Adjust `snapshotInterval` to 120 seconds
4. Allocate more RAM: `java -Xmx512M -jar discord-antinuke-1.0.0.jar`

---

**You're now ready to protect your Discord server!** 🛡️
