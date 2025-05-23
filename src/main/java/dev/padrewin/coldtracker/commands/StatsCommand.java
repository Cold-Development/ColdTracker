package dev.padrewin.coldtracker.commands;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.database.MySQLStorageHandler;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class StatsCommand extends BaseCommand {

    public StatsCommand() {
        super("stats", CommandManager.CommandAliases.STATS);
    }

    @Override
    public void execute(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);
        String prefix = localeManager.getLocaleMessage("prefix");

        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage(prefix + localeManager.getLocaleMessage("command-stats-console-no-self"));
            return;
        }

        if (args.length == 0 && sender instanceof Player) {
            Player player = (Player) sender;
            showStats(plugin, localeManager, sender, player.getUniqueId(), player.getName(), true);
            return;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player) || sender.hasPermission("coldtracker.stats.others")) {
                String targetName = args[0];
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);

                if (!targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage(prefix + localeManager.getLocaleMessage("player-not-found").replace("{player}", targetName));
                    return;
                }

                UUID targetUUID = targetPlayer.getUniqueId();

                plugin.getLuckPerms().getUserManager().loadUser(targetUUID).thenAcceptAsync(user -> {
                    if (user == null) {
                        sender.sendMessage(prefix + localeManager.getLocaleMessage("player-not-found").replace("{player}", targetName));
                        return;
                    }

                    boolean trackTime = user.getCachedData().getPermissionData().checkPermission("coldtracker.tracktime").asBoolean();
                    boolean trackVotes = user.getCachedData().getPermissionData().checkPermission("coldtracker.trackvote").asBoolean();

                    if (!trackTime && !trackVotes) {
                        sender.sendMessage(prefix + localeManager.getLocaleMessage("no-staff-member").replace("{player}", targetName));
                        return;
                    }

                    showStats(plugin, localeManager, sender, targetUUID, targetName, false);
                });
            } else {
                sender.sendMessage(prefix + localeManager.getLocaleMessage("no-permission"));
            }
        } else {
            sender.sendMessage(prefix + localeManager.getLocaleMessage("invalid-command-usage"));
        }
    }

    private void showStats(ColdTracker plugin, LocaleManager localeManager, CommandSender sender, UUID playerUUID, String playerName, boolean isSelf) {
        String prefix = localeManager.getLocaleMessage("prefix");
        boolean isMySQL = plugin.getStorageHandler() instanceof MySQLStorageHandler;

        if (isMySQL) {
            // MySQL: Show detailed server-by-server stats
            showMultiServerStats(plugin, localeManager, sender, playerUUID, playerName, isSelf, prefix);
        } else {
            // SQLite: Show simple single-server stats
            showSingleServerStats(plugin, localeManager, sender, playerUUID, playerName, isSelf, prefix);
        }
    }

    /**
     * Show stats for single server (SQLite)
     */
    private void showSingleServerStats(ColdTracker plugin, LocaleManager localeManager, CommandSender sender, UUID playerUUID, String playerName, boolean isSelf, String prefix) {
        plugin.getStorageHandler().getTotalTimeAsync(playerUUID).thenAccept(totalTime -> {
            StringBuilder statsMessage = new StringBuilder();
            statsMessage.append(" \n");

            if (isSelf) {
                statsMessage.append(prefix).append(localeManager.getLocaleMessage("command-stats-self-title")).append("\n");
            } else {
                statsMessage.append(prefix).append(
                        localeManager.getLocaleMessage("command-stats-other-title").replace("{player}", playerName)
                ).append("\n");
            }

            // Show simple playtime (no server breakdown)
            statsMessage.append(prefix).append(
                    localeManager.getLocaleMessage("command-stats-playtime-prefix")
                            .replace("{time}", formatTime(totalTime))
            ).append("\n");

            if (plugin.getConfig().getBoolean("track-votes", false)) {
                plugin.getStorageHandler().getTotalVotesAsync(playerUUID).thenAccept(totalVotes -> {
                    statsMessage.append(prefix).append(
                            localeManager.getLocaleMessage("command-stats-votes-prefix")
                                    .replace("{votes}", String.valueOf(totalVotes))
                    ).append("\n\n");

                    sendFormatted(statsMessage.toString(), sender);
                });
            } else {
                statsMessage.append(" ");
                sendFormatted(statsMessage.toString(), sender);
            }
        });
    }

    /**
     * Show stats for multiple servers (MySQL)
     */
    private void showMultiServerStats(ColdTracker plugin, LocaleManager localeManager, CommandSender sender, UUID playerUUID, String playerName, boolean isSelf, String prefix) {
        plugin.getStorageHandler().getPlaytimeByServer(playerUUID).thenAccept(playtimeMap -> {
            long totalMillis = 0;

            StringBuilder statsMessage = new StringBuilder();
            statsMessage.append(" \n");

            if (isSelf) {
                statsMessage.append(prefix).append(localeManager.getLocaleMessage("command-stats-self-title")).append("\n");
            } else {
                statsMessage.append(prefix).append(
                        localeManager.getLocaleMessage("command-stats-other-title").replace("{player}", playerName)
                ).append("\n");
            }

            // Only show server breakdown if there are multiple servers
            if (playtimeMap.size() > 1) {
                statsMessage.append(prefix).append(localeManager.getLocaleMessage("command-stats-total-header")).append("\n");

                for (Map.Entry<String, Long> entry : playtimeMap.entrySet()) {
                    String server = entry.getKey();
                    long millis = entry.getValue();
                    totalMillis += millis;

                    String timeFormatted = formatTime(millis);
                    statsMessage.append(prefix).append(
                            localeManager.getLocaleMessage("command-stats-total-server-line")
                                    .replace("{server}", server)
                                    .replace("{time}", timeFormatted)
                    ).append("\n");
                }

                statsMessage.append(prefix).append(
                        localeManager.getLocaleMessage("command-stats-total-combined")
                                .replace("{time}", formatTime(totalMillis))
                ).append("\n");
            } else {
                // Single server in MySQL - show simple format
                for (long millis : playtimeMap.values()) {
                    totalMillis += millis;
                }

                statsMessage.append(prefix).append(
                        localeManager.getLocaleMessage("command-stats-playtime-prefix")
                                .replace("{time}", formatTime(totalMillis))
                ).append("\n");
            }

            if (plugin.getConfig().getBoolean("track-votes", false)) {
                plugin.getStorageHandler().getTotalVotesAsync(playerUUID).thenAccept(totalVotes -> {
                    statsMessage.append(prefix).append(
                            localeManager.getLocaleMessage("command-stats-votes-prefix")
                                    .replace("{votes}", String.valueOf(totalVotes))
                    ).append("\n\n");

                    sendFormatted(statsMessage.toString(), sender);
                });
            } else {
                statsMessage.append(" ");
                sendFormatted(statsMessage.toString(), sender);
            }
        });
    }

    private void sendFormatted(String raw, CommandSender sender) {
        for (String line : raw.split("\n")) {
            sender.sendMessage(line.isEmpty() ? " " : line);
        }
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long days = totalSeconds / 86400;
        long remaining = totalSeconds % 86400;

        long hours = remaining / 3600;
        remaining %= 3600;

        long minutes = remaining / 60;
        long seconds = remaining % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || (days == 0 && hours == 0 && minutes == 0)) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }

    @Override
    public List<String> tabComplete(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("coldtracker.stats.others")) {
            return null;
        }
        return Collections.emptyList();
    }
}