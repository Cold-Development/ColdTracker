![ColdTracker](https://imgur.com/U8vZF1V.gif)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/Cold-Development/ColdTracker/total?style=flat&logo=github&color=04be00&link=https%3A%2F%2Fgithub.com%2FCold-Development%2FColdTracker%2Freleases)
![GitHub Actions Workflow Status](https://img.shields.io/github/actions/workflow/status/Cold-Development/ColdTracker/release.yml?logo=github&link=https%3A%2F%2Fgithub.com%2FCold-Development%2FColdTracker%2Factions)
![Jenkins Build](https://img.shields.io/jenkins/build?jobUrl=https%3A%2F%2Fjenkins.colddev.dev%2Fjob%2FCold%2520Development%2Fjob%2FColdTracker%2F&logo=jenkins&logoColor=white&label=Jenkins%20build)


- ColdTracker is an advanced plugin for Minecraft servers that enhances the management of staff members' time spent on the server. This plugin provides an accurate way to track staff members active hours, saving all their play sessions in a database to evaluate each member's contribution to community activities.
- Supports `SQLite` database.
- This plugin is based on [`ColdDev`](https://github.com/Cold-Development/ColdDev) library.

---
## 📖 Documentation
All information is included and can be found in this repository's [**Wiki**](https://github.com/Cold-Development/ColdTracker/wiki).<br>
For any questions or support, you can join our [**Discord server**](https://discord.colddev.dev). Here you can find all the support you need.<br>
![ColdTracker](https://imgur.com/vUpbz8I.png)

---
## ⚙️ Server compatibility<br>
ColdTracker is compatible with Spigot and any forks of it.<br>
> [!NOTE]
> Recommending using Paper.<br>
> CraftBukkit is **NOT** and **will NOT** be supported.

---
## 🖥 Commands & permissions
- `/coldtracker` - shows plugin information
- `/coldtracker showtime <playername>` - shows total time spent by a player on your server
- `/coldtracker wipe` - wipe out entire database

  - `coldtracker.showtime` - can use `/coldtracker showtime <playername>` command
  - `coldtracker.wipe` - can wipe out the database of **ColdTracker** plugin
  - `coldtracker.reload` - can reload config and locales
  - `coldtracker.dump` - can dump data from database into a Gist link (**token needed**)
  - `coldtracker.tracktime` - this is the permission that is checked by the plugin. So if you want to look after your staff activity, this permission is a **must** to be included in their rank permissions.
> [!WARNING]
> Do not grant anyone **`coldtracker.wipe`** permission since it can wipe out the plugin's database and lose all your staff tracks.<br>
> Same applies for **`coldtracker.dump`** permission since it can dump your database information.<br>
> Use it only when you're absolutely sure that you want to reset the progress.

---
## ⚙ Installation
1. Visit [GitHub release](https://github.com/Cold-Development/ColdTracker/releases) page.
2. Download the plugin.
3. Put the `.jar` file in your `~/plugins` folder.
4. Start up the server.<br>
After finishing these steps, you should be able to see `~/plugins/ColdTracker` directory, which contains all files that may interest you.<br>
> [!WARNING]
> After installation, you might also find a new folder named `ColdDev`.<br>
> **DO NOT** delete. This is the structure of ColdTracker plugin.

![](https://raw.githubusercontent.com/mayhemantt/mayhemantt/Update/svg/Bottom.svg)
