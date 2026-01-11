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
| `/changelog` | Display changelog help | `changelogbook.use` |
| `/changelog add <content>` | Add a new changelog entry | `changelogbook.admin` |
| `/changelog edit <id> <content>` | Edit an existing entry | `changelogbook.admin` |
| `/changelog delete <id>` | Delete a changelog entry | `changelogbook.admin` |
| `/changelog list` | List all entries with IDs | `changelogbook.admin` |
| `/changelog reload` | Reload the configuration | `changelogbook.admin` |
| `/changelog debug <on\|off>` | Toggle debug mode | `changelogbook.admin` |

## Color Formatting Support

ChangelogBook supports rich text formatting in changelog entries with automatic version detection:

### Supported Color Codes

#### Legacy Color Codes (Minecraft 1.8+)
All standard Minecraft color codes are supported using the `&` prefix:

| Code | Color | Example |
|------|-------|---------|
| `&0` | Black | `&0Black text` |
| `&1` | Dark Blue | `&1Dark blue text` |
| `&2` | Dark Green | `&2Dark green text` |
| `&3` | Dark Aqua | `&3Dark aqua text` |
| `&4` | Dark Red | `&4Dark red text` |
| `&5` | Dark Purple | `&5Dark purple text` |
| `&6` | Gold | `&6Gold text` |
| `&7` | Gray | `&7Gray text` |
| `&8` | Dark Gray | `&8Dark gray text` |
| `&9` | Blue | `&9Blue text` |
| `&a` | Green | `&aGreen text` |
| `&b` | Aqua | `&bAqua text` |
| `&c` | Red | `&cRed text` |
| `&d` | Light Purple | `&dLight purple text` |
| `&e` | Yellow | `&eYellow text` |
| `&f` | White | `&fWhite text` |

#### Format Codes (Minecraft 1.8+)
| Code | Effect | Example |
|------|--------|---------|
| `&l` | **Bold** | `&lBold text` |
| `&o` | *Italic* | `&oItalic text` |
| `&n` | Underline | `&nUnderlined text` |
| `&m` | ~~Strikethrough~~ | `&mStrikethrough text` |
| `&k` | Obfuscated | `&kObfuscated text` |
| `&r` | Reset | `&cRed &rNormal` |

#### Hex Colors (Minecraft 1.16+)
Custom hex colors using the format `&#RRGGBB`:

```
&#FF5733 - Orange
&#00FF00 - Bright Green
&#3498DB - Light Blue
&#9B59B6 - Purple
&#E74C3C - Red
&#F39C12 - Orange-Yellow
```

**Version Compatibility:**
- On Minecraft 1.16+: Hex colors render with full RGB precision
- On Minecraft 1.8-1.15: Hex colors automatically convert to the nearest legacy color

### Usage Examples

#### Simple Colors
```
/changelog add &6Welcome to our server!
/changelog add &aNew feature added! &eCheck it out!
```

#### Bold and Colored
```
/changelog add &l&4IMPORTANT: &r&cServer maintenance tonight
/changelog add &6&lNEW FEATURE: &r&eCustom enchantments
```

#### Hex Colors (1.16+)
```
/changelog add &#FF5733Custom orange color!
/changelog add &#3498DBThis is light blue text
/changelog add &#9B59B6Beautiful purple announcement
```

#### Mixed Formatting
```
/changelog add &6&l[UPDATE] &r&eAdded new &a&lVIP ranks&r&e!
/changelog add &#FF5733&lHot Update: &r&#FFA500New items in shop
/changelog add &b&nClick here &r&7for more info
```

### Color Tips

1. **Combine codes**: Use multiple codes together (e.g., `&l&6Bold gold text`)
2. **Reset formatting**: Use `&r` to reset all colors and formatting
3. **Test colors**: Use `/changelog list` in console to preview colored entries
4. **Hex on old versions**: Don't worry about compatibility - hex colors automatically downgrade on older servers

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

# Auto-open changelog book on player join
auto-open:
  enabled: false  # Set to true to automatically open book when players join
  delay-seconds: 3  # Wait time in seconds before opening (e.g., 5 = wait 5 seconds)

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

### Auto-Open Feature

When `auto-open.enabled` is set to `true`, the changelog book will automatically open for players when they join the server, provided there are new entries they haven't seen yet. This replaces the chat notification with direct book opening.

- **enabled**: Set to `true` to enable auto-opening, `false` to use chat notifications instead
- **delay-seconds**: Time in seconds to wait after join before opening the book (recommended: 3-5 seconds)

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
