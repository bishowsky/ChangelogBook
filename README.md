# ChangelogBook

A Minecraft plugin for Paper 1.21.3 that allows server administrators to manage and display changelogs in-game using a custom book GUI.

## Features

- 📖 **Interactive Book GUI** - Players view changelogs in a beautifully formatted in-game book
- 🔢 **Sequential Numbering** - Entries are displayed with easy-to-read numbers (#1, #2, #3)
- 🗄️ **MySQL Support** - Store all changelog entries in a MySQL database
- 🌍 **Multi-Language** - Built-in support for English and Polish
- 🎁 **Reward System** - Incentivize players to read updates with customizable rewards
- 📢 **Join Notifications** - Alert players about new changelog entries when they join
- ⚡ **Admin Commands** - Easy-to-use commands for managing changelog entries
- 🔄 **Hot Reload** - Reload configuration without restarting the server

## Commands

| Command | Description | Permission |
|---------|-------------|------------|
| `/changelog` | View the changelog book | `changelogbook.use` |
| `/changelog add <content>` | Add a new changelog entry | `changelogbook.admin` |
| `/changelog edit <id> <content>` | Edit an existing entry | `changelogbook.admin` |
| `/changelog delete <id>` | Delete a changelog entry | `changelogbook.admin` |
| `/changelog list` | List all entries with IDs | `changelogbook.admin` |
| `/changelog reload` | Reload the configuration | `changelogbook.admin` |
| `/changelog debug <on\|off>` | Toggle debug mode | `changelogbook.admin` |

## Permissions

- `changelogbook.use` - Allows players to view the changelog (default: true)
- `changelogbook.admin` - Allows access to all admin commands (default: op)

## Installation

1. Download the latest `changelogbook.jar` from [Releases](https://github.com/bishowsky/ChangelogBook/releases)
2. Place the jar file in your server's `plugins` folder
3. Restart your server
4. Configure the plugin in `plugins/ChangelogBook/config.yml`
5. (Optional) Set up MySQL database connection

## Configuration

```yaml
# Language: en (English) or pl (Polish)
language: "en"

# Delay time (in ticks) before showing notification after joining
notification-delay: 60

# MySQL Database Configuration
mysql:
  enabled: false
  host: 127.0.0.1
  port: 3306
  database: minecraft
  username: root
  password: "change_me"
  table-prefix: changelog_

# Reward System Configuration
rewards:
  enabled: true
  types:
    bronze:
      enabled: true
      chance: 20
      cooldown-hours: 4
      max-days: 7
      command: "give %player% diamond 1"
```

## Database

The plugin supports both YAML and MySQL storage:

- **YAML** - Default storage method, entries saved in `data.yml`
- **MySQL** - Recommended for larger servers, set `mysql.enabled: true` in config

### MySQL Tables

The plugin automatically creates the following tables:
- `changelog_entries` - Stores all changelog entries
- `changelog_last_seen` - Tracks when players last viewed the changelog

## Reward System

Players can receive rewards for reading new changelog entries:

- Multiple reward tiers (bronze, silver, gold)
- Customizable chance percentages
- Cooldown system to prevent spam
- Age limit for eligible entries
- Execute any command as reward

## Language Support

The plugin includes two language files:
- `languages/en.yml` - English (default)
- `languages/pl.yml` - Polish

All messages are fully customizable through these files.

## Requirements

- **Minecraft Version**: 1.21.3
- **Server Software**: Paper (or compatible forks)
- **Java Version**: 17 or higher
- **Optional**: MySQL 5.7+ or MariaDB 10.2+

## Building from Source

```bash
git clone https://github.com/bishowsky/ChangelogBook.git
cd ChangelogBook
./gradlew clean build
```

The compiled jar will be in `app/build/libs/changelogbook.jar`

## Support

For bugs, suggestions, or questions, please open an issue on [GitHub](https://github.com/bishowsky/ChangelogBook/issues).

## License

This project is licensed under the MIT License.

## Author

Created by **Bishyy**
