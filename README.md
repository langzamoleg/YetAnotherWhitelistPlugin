# YAWL (Yet Another Whitelist Plugin)

**YAWL** is a lightweight, efficient, and feature-rich whitelist plugin designed exclusively for **Velocity** proxy servers. Built with simplicity and performance in mind, YAWL provides a robust solution for managing player access with support for both temporary and permanent whitelisting.

## 🚀 Overview

YAWL combines simplicity with powerful features. It uses **Redis** for distributed whitelist storage, making it perfect for multi-proxy setups. The plugin is designed to be performant and unobtrusive, ensuring zero impact on your server's performance while providing enterprise-grade whitelist management.

## ✨ Features

  * **⚡ Lightweight & Fast:** Minimal footprint with zero performance impact on your proxy.
  * **🔴 Redis Storage:** Distributed whitelist storage with Redis for multi-proxy setups and high availability.
  * **⏱️ Temporary Whitelisting:** Add players with expiration times (e.g., `7d`, `30d`, `1mo`, `1y`).
  * **🔄 Auto-Expiry:** Automatically kicks players when their whitelist access expires.
  * **⚙️ Simple Configuration:** Clean `config.toml` file for easy customization.
  * **🌍 Multi-Language Support:** Built-in support for 11 languages (`en`, `ar`, `de`, `es`, `fr`, `ja`, `ru`, `uk`, `zh-cn`, `pt-br`, `tr`).
  * **🎯 Client-Language Detection:** Automatically displays messages in players' client language.
  * **📊 PlaceholderAPI Integration:** Show remaining whitelist time on backend servers (requires [YetAnotherWhitelistCompanion](https://github.com/renwixx/YetAnotherWhitelistCompanion)).
  * **🔄 Live Reload:** Update configuration and whitelist without restarting the proxy.
  * **🔒 Fine-Grained Permissions:** Complete control over who can manage the whitelist.

## 📦 Installation

### Prerequisites
- **Velocity** proxy server (version 3.4.0 or higher)
- **Redis** server (for distributed whitelist storage)

### Steps
1. Download the latest version from [Releases](https://github.com/renwixx/YetAnotherWhitelistPlugin/releases) or [Modrinth](https://modrinth.com/plugin/yawl).
2. Place the `.jar` file into the `plugins` folder of your Velocity proxy.
3. Configure Redis connection in `plugins/yawl/config.toml` (generated on first start).
4. Start or restart your proxy.
5. Customize locales and settings as needed.

## ⚙️ Configuration

Configuration is located in `plugins/yawl/config.toml`:

```toml
[settings]
# Enable or disable the whitelist
enabled = true

# Default language for messages (en, ru, uk, de, fr, es, ar, zh-cn, ja, pt-br, tr)
locale = "en"

# Use player's client language if available
use-client-locale = false

# Case-sensitive player names (recommended: false)
case-sensitive = false

# Kick players immediately when removed from whitelist or access expires
kick-active-on-revoke = true

# Interval for updating placeholders on backend servers (in minutes)
placeholder-reload-interval = 2

[redis]
# Redis server connection URL
url = "redis://localhost:6379"

# Redis password (leave empty if not required)
password = ""
```

### Redis Setup
YAWL uses Redis for distributed whitelist storage, which enables:
- **Multi-proxy support:** Share whitelist across multiple Velocity instances
- **High availability:** Redis persistence ensures whitelist data survives restarts
- **Real-time sync:** Changes are instantly available across all proxies

Make sure your Redis server is running and accessible before starting the plugin.

## 🆔 Placeholders
Starting with version 1.2, you can use the `%yawl_duration%` placeholder, which contains the amount of time remaining for the player. To use placeholders, you need to install [YetAnotherWhitelistCompanion](https://github.com/renwixx/YetAnotherWhitelistCompanion) and [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) on your backend server(s).

<img width="317" height="65" alt="image" src="https://github.com/user-attachments/assets/324a17f7-7823-4b67-824c-14c1edcb69b6" />
<img width="317" height="65" alt="image" src="https://github.com/user-attachments/assets/06db7511-df7d-49ef-a690-b9c10ff0b793" />
<img width="317" height="65" alt="image" src="https://github.com/user-attachments/assets/338d68c9-2003-41c1-a922-3436381522ad" />


## 💬 Commands

All commands start with `/yawl`.

| Command                           | Description                                         |
| --------------------------------- | --------------------------------------------------- |
| `/yawl`                           | Displays the plugin help message.                   |
| `/yawl add <player>`              | Adds a player to the whitelist.                     |
| `/yawl add <player> [<duration>]` | Adds a player to the whitelist for a specific time. |
| `/yawl extend <player> [<duration>] [add\|replace]` | Adds time for a specific player. If the white list timer has expired, it will suggest replacing the time based on the actual time or adding to the old time limit. |
| `/yawl remove <player>`           | Removes a player from the whitelist.                |
| `/yawl list`                      | Shows a list of all whitelisted players.            |
| `/yawl reload`                    | Reloads the config and `whitelist.txt`.             |

## 🔑 Permissions

Grant these permissions to your staff groups to control who can manage the whitelist.

| Permission             | Description                                                   |
| ---------------------- | ------------------------------------------------------------- |
| `yawl.bypass`          | Allows a player to join the server even if not on the whitelist. |
| `yawl.command.add`     | Allows using the `/yawl add` command.                         |
| `yawl.command.extend`  | Allows using the `/yawl extend` command.                      |
| `yawl.command.remove`  | Allows using the `/yawl remove` command.                      |
| `yawl.command.list`    | Allows using the `/yawl list` command.                        |
| `yawl.command.reload`  | Allows using the `/yawl reload` command.                      |
