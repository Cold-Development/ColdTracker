![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/Cold-Development/ColdTracker/total?style=flat&logo=github&color=04be00&link=https%3A%2F%2Fgithub.com%2FCold-Development%2FColdTracker%2Freleases)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/Cold-Development/ColdTracker/release.yml?logo=github&link=https%3A%2F%2Fgithub.com%2FCold-Development%2FColdTracker%2Factions)


ColdTracker is an advanced plugin for Minecraft servers that enhances the management of staff members' time spent on the server. This plugin provides an accurate way to track staff members' active hours, saving all their play sessions in a database to evaluate each member's contribution to community activities.

> [!NOTE]
> This plugin is in development phase.<br>
> You will see those kind of messages in your console.<br>

![ColdTracker](https://imgur.com/D6kcZTK.png)

## 📦 Features:
- Gameplay Time Tracking: ColdTracker allows you to monitor how much time each staff member spends on the server. Data is saved in an SQLite database, allowing you to consult detailed statistics about each member's activity.<br>
- LuckPerms Compatibility: The plugin integrates with LuckPerms to dynamically check players' permissions, even if they are offline. Thus, only staff members with the appropriate permissions will be tracked by the plugin.<br>
- Useful Commands: ColdTracker includes commands such as /coldtracker showtime <player> to check the total playtime of a specific staff member, as well as /coldtracker reload to reload the configuration files without restarting the server.<br>
- Customizable Locale: The plugin provides support for local messages, allowing all messages displayed by the plugin to be customized to match your server's style. The custom prefix makes messages more visible and cohesive.<br>
- Debug Messages: ColdTracker includes a configurable debug option that, when enabled, displays detailed information in the console about player join and leave events, helping diagnose issues.<br>
- Database support: ColdTracker supports SQLite database. <br>

---
## 🖥 Commands & permissions
- `/coldtracker` - shows plugin information
- `/coldtracker showtime <playername>` - shows total time spent by a player on your server
- `/coldtracker wipe` - wipe out entire database

  - `coldtracker.showtime` - can use `/coldtracker showtime <playername>` command
  - `coldtracker.wipe` - can wipe out the database of **ColdTracker** plugin
  - `coldtracker.reload` - can reload config and locales
  - `coldtracker.tracktime` - this is the permission that is checked by the plugin. So if you want to look after your staff activity, this permission is a **must** to be included in their rank permissions.
> [!WARNING]
> Do not grant anyone `coldtracker.wipe` permission since it can wipe out the plugin's database and lose all your staff tracks.<br>
> Use it only when you're absolutely sure that you want to reset the progress.
---
## ⚙ Installation
1. Install the plugin in your server's plugins folder.<br>
2. Ensure you have LuckPerms installed on the server for permission checking.<br>
3. Configure the locale files and debug options according to your preferences.<br>
4. Enjoy detailed monitoring of your staff's activity!<br>

![ColdTracker](https://imgur.com/vUpbz8I.png)
