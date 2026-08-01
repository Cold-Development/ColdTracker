package dev.padrewin.coldtracker.commands;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.listeners.PlayerTrackingListener;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import dev.padrewin.coldtracker.setting.SettingKey;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
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

                    if (!trackTime) {
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

        // Try to get real-time data first, fallback to database
        PlayerTrackingListener trackingListener = plugin.getPlayerTrackingListener();
        if (trackingListener != null && trackingListener.hasRealtimeData(playerUUID)) {
            // Use real-time data
            long totalTime = trackingListener.getRealtimeTotalTime(playerUUID);
            displayStats(plugin, localeManager, sender, playerUUID, playerName, totalTime, isSelf);
        } else {
            // Fallback to database query
            plugin.getDatabaseManager().getTotalTimeAsync(playerUUID).thenAccept(totalTime -> {
                displayStats(plugin, localeManager, sender, playerUUID, playerName, totalTime, isSelf);
            });
        }
    }

    private void displayStats(ColdTracker plugin, LocaleManager localeManager, CommandSender sender, UUID playerUUID, String playerName, long totalTime, boolean isSelf) {
        String prefix = localeManager.getLocaleMessage("prefix");

        long totalSeconds = totalTime / 1000;
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

        String timeFormatted = sb.toString().trim();

        StringBuilder statsMessage = new StringBuilder();
        statsMessage.append(" \n");

        if (isSelf) {
            statsMessage.append(prefix).append(localeManager.getLocaleMessage("command-stats-self-title")).append("\n");
        } else {
            statsMessage.append(prefix).append(
                    localeManager.getLocaleMessage("command-stats-other-title").replace("{player}", playerName)
            ).append("\n");
        }

        statsMessage.append(prefix).append(
                localeManager.getLocaleMessage("command-stats-playtime-prefix").replace("{time}", timeFormatted)
        ).append("\n");

        List<CompletableFuture<String>> extraBlocks = new ArrayList<>();

        if (plugin.getConfig().getBoolean(SettingKey.TRACK_VOTES.getKey(), false)) {
            extraBlocks.add(plugin.getDatabaseManager().getTotalVotesAsync(playerUUID).thenApply(totalVotes ->
                    prefix + localeManager.getLocaleMessage("command-stats-votes-prefix")
                            .replace("{votes}", String.valueOf(totalVotes)) + "\n"
            ));
        }

        if (!isSelf && sender.hasPermission("coldtracker.stats.others")
                && plugin.getConfig().getBoolean(SettingKey.TRACK_SANCTIONS.getKey(), false)
                && plugin.getLiteBansHook() != null && plugin.getLiteBansHook().isAvailable()) {
            extraBlocks.add(plugin.getDatabaseManager().getSanctionsPeriodStartAsync()
                    .thenCompose(periodStart -> plugin.getLiteBansHook().getSanctionCountsAsync(playerUUID, periodStart))
                    .thenApply(counts -> {
                        StringBuilder block = new StringBuilder();
                        ShowSanctionsCommand.appendSanctionsBlock(block, localeManager, prefix, counts);
                        return block.toString();
                    }));
        }

        CompletableFuture.allOf(extraBlocks.toArray(new CompletableFuture[0])).thenRun(() -> {
            for (CompletableFuture<String> block : extraBlocks) {
                statsMessage.append(block.join());
            }
            statsMessage.append(" \n");

            for (String line : statsMessage.toString().split("\n")) {
                sender.sendMessage(line.isEmpty() ? " " : line);
            }
        });
    }

    @Override
    public List<String> tabComplete(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("coldtracker.stats.others")) {
            return null;
        }
        return Collections.emptyList();
    }
}