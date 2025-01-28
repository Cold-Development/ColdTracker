package dev.padrewin.coldtracker.commands;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class StatsCommand extends BaseCommand {

    public StatsCommand() {
        super("stats", CommandManager.CommandAliases.STATS);
    }

    @Override
    public void execute(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);
        String prefix = localeManager.getLocaleMessage("prefix");

        // Console handling
        if (!(sender instanceof Player)) {
            if (args.length == 1) {
                String targetName = args[0];
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);

                if (!targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage(prefix + localeManager.getLocaleMessage("player-not-found").replace("{player}", targetName));
                    return;
                }

                UUID targetUUID = targetPlayer.getUniqueId();
                showStats(plugin, localeManager, sender, targetUUID, targetPlayer.getName());
            } else {
                sender.sendMessage(prefix + localeManager.getLocaleMessage("command-stats-console-only"));
            }
            return;
        }

        // Player handling
        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        if (args.length == 0) {
            // Self stats
            showStats(plugin, localeManager, sender, playerUUID, player.getName());
        } else if (args.length == 1) {
            // Target stats
            if (!sender.hasPermission("coldtracker.stats.others")) {
                sender.sendMessage(prefix + localeManager.getLocaleMessage("no-permission"));
                return;
            }

            String targetName = args[0];
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);

            if (!targetPlayer.hasPlayedBefore()) {
                sender.sendMessage(prefix + localeManager.getLocaleMessage("player-not-found").replace("{player}", targetName));
                return;
            }

            UUID targetUUID = targetPlayer.getUniqueId();
            boolean trackTime = targetPlayer.isOnline() && targetPlayer.getPlayer().hasPermission("coldtracker.tracktime");
            boolean trackVotes = targetPlayer.isOnline() && targetPlayer.getPlayer().hasPermission("coldtracker.trackvote");

            if (!trackTime && !trackVotes) {
                sender.sendMessage(prefix + localeManager.getLocaleMessage("no-staff-member").replace("{player}", targetName));
                return;
            }

            showStats(plugin, localeManager, sender, targetUUID, targetPlayer.getName());
        } else {
            sender.sendMessage(prefix + localeManager.getLocaleMessage("invalid-command-usage"));
        }
    }

    private void showStats(ColdTracker plugin, LocaleManager localeManager, CommandSender sender, UUID playerUUID, String playerName) {
        String prefix = localeManager.getLocaleMessage("prefix");
        long totalTime = plugin.getDatabaseManager().getTotalTime(playerUUID);
        long hours = (totalTime / 1000) / 3600;
        long minutes = ((totalTime / 1000) % 3600) / 60;
        long seconds = (totalTime / 1000) % 60;
        long days = hours / 24;
        hours = hours % 24;
        String timeFormatted = String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);

        String statsMessage = prefix + localeManager.getLocaleMessage("command-stats-playtime").replace("{time}", timeFormatted);

        if (plugin.getConfig().getBoolean("track-votes", false)) {
            int totalVotes = plugin.getDatabaseManager().getTotalVotes(playerUUID);
            statsMessage += " " + localeManager.getLocaleMessage("command-stats-votes").replace("{votes}", String.valueOf(totalVotes));
        }

        sender.sendMessage(statsMessage);
    }

    @Override
    public List<String> tabComplete(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("coldtracker.stats.others")) {
            return null;
        }
        return Collections.emptyList();
    }
}