package dev.padrewin.coldtracker.commands;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import net.luckperms.api.LuckPerms;

public class ShowTimeCommand extends BaseCommand {

    public ShowTimeCommand() {
        super("showtime", CommandManager.CommandAliases.SHOWTIME);
    }

    @Override
    public void execute(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);

        if (!sender.hasPermission("coldtracker.showtime")) {
            localeManager.sendMessage(sender, "no-permission");
            return;
        }

        if (args.length != 1) {
            localeManager.sendMessage(sender, "invalid-command-usage");
            return;
        }

        String playerName = args[0];
        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(playerName);

        if (!targetPlayer.hasPlayedBefore()) {
            String prefix = localeManager.getLocaleMessage("prefix");
            String message = prefix + localeManager.getLocaleMessage("player-not-found").replace("{player}", playerName);
            sender.sendMessage(message);
            return;
        }

        UUID playerUUID = targetPlayer.getUniqueId();

        LuckPerms luckPerms = plugin.getLuckPerms();
        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(playerUUID);

        userFuture.thenAccept(user -> {
            if (user != null) {
                boolean hasPermission = user.getCachedData().getPermissionData().checkPermission("coldtracker.tracktime").asBoolean();
                if (!hasPermission) {
                    String prefix = localeManager.getLocaleMessage("prefix");
                    String message = prefix + localeManager.getLocaleMessage("no-staff-member").replace("{player}", playerName);
                    sender.sendMessage(message);
                    return;
                }

                long totalTime = plugin.getDatabaseManager().getTotalTime(playerUUID);

                long hours = (totalTime / 1000) / 3600;
                long minutes = ((totalTime / 1000) % 3600) / 60;
                long seconds = (totalTime / 1000) % 60;

                long days = hours / 24;
                hours = hours % 24;
                String prefix = localeManager.getLocaleMessage("prefix");
                String timeFormatted = String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
                String message = prefix + localeManager.getLocaleMessage("showtime-message")
                        .replace("{player}", targetPlayer.getName() != null ? targetPlayer.getName() : playerName)
                        .replace("{time}", timeFormatted);
                sender.sendMessage(message);
            } else {
                sender.sendMessage(localeManager.getLocaleMessage("player-not-found").replace("{player}", playerName));
            }
        });
    }

    @Override
    public List<String> tabComplete(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return null;
        }
        return Collections.emptyList();
    }
}
