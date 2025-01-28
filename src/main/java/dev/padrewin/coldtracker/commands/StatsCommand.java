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

        // Dacă comanda vine din consolă fără argumente
        if (!(sender instanceof Player) && args.length == 0) {
            sender.sendMessage(prefix + localeManager.getLocaleMessage("command-stats-console-no-self"));
            return;
        }

        // Dacă comanda vine de la un jucător și nu are argumente
        if (args.length == 0 && sender instanceof Player) {
            Player player = (Player) sender;
            showStats(plugin, localeManager, sender, player.getUniqueId(), player.getName());
            return;
        }

        // Dacă există argumente, verificăm permisiunile
        if (args.length == 1) {
            if (!(sender instanceof Player) || sender.hasPermission("coldtracker.stats.others")) {
                String targetName = args[0];
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);

                if (!targetPlayer.hasPlayedBefore()) {
                    sender.sendMessage(prefix + localeManager.getLocaleMessage("player-not-found").replace("{player}", targetName));
                    return;
                }

                UUID targetUUID = targetPlayer.getUniqueId();

                // Verificăm permisiunile folosind LuckPerms
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

                    showStats(plugin, localeManager, sender, targetUUID, targetName);
                });
            } else {
                sender.sendMessage(prefix + localeManager.getLocaleMessage("no-permission"));
            }
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

        StringBuilder statsMessage = new StringBuilder();

        boolean trackVotes = plugin.getConfig().getBoolean("track-votes", false);
        int totalVotes = trackVotes ? plugin.getDatabaseManager().getTotalVotes(playerUUID) : 0;

        // Dacă targetul este senderul
        if (sender instanceof Player && ((Player) sender).getUniqueId().equals(playerUUID)) {
            String playtimeMessage = localeManager.getLocaleMessage("command-stats-playtime").replace("{time}", timeFormatted);
            // Eliminăm punctul doar dacă voturile sunt trackate
            if (trackVotes) {
                playtimeMessage = playtimeMessage.endsWith(".") ? playtimeMessage.substring(0, playtimeMessage.length() - 1) : playtimeMessage;
            }
            statsMessage.append(prefix).append(playtimeMessage);
        } else {
            String playtimeMessage = localeManager.getLocaleMessage("showtime-message").replace("{player}", playerName).replace("{time}", timeFormatted);
            // Eliminăm punctul doar dacă voturile sunt trackate
            if (trackVotes) {
                playtimeMessage = playtimeMessage.endsWith(".") ? playtimeMessage.substring(0, playtimeMessage.length() - 1) : playtimeMessage;
            }
            statsMessage.append(prefix).append(playtimeMessage);
        }

        // Adăugăm voturile doar dacă sunt trackate
        if (trackVotes) {
            statsMessage.append(" ").append(localeManager.getLocaleMessage("command-stats-votes").replace("{votes}", String.valueOf(totalVotes)));
        }

        sender.sendMessage(statsMessage.toString());
    }



    @Override
    public List<String> tabComplete(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("coldtracker.stats.others")) {
            return null;
        }
        return Collections.emptyList();
    }
}