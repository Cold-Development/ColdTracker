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

public class ShowVotesCommand extends BaseCommand {

    public ShowVotesCommand() {
        super("showvotes", CommandManager.CommandAliases.SHOWVOTES);
    }

    @Override
    public void execute(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);

        if (!plugin.getConfig().getBoolean("track-votes", false)) {
            localeManager.sendMessage(sender, "command-showvotes-disabled");
            return;
        }

        if (!sender.hasPermission("coldtracker.showvotes")) {
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
                boolean hasPermission = user.getCachedData().getPermissionData().checkPermission("coldtracker.trackvote").asBoolean();
                if (!hasPermission) {
                    String prefix = localeManager.getLocaleMessage("prefix");
                    String message = prefix + localeManager.getLocaleMessage("no-staff-member").replace("{player}", playerName);
                    sender.sendMessage(message);
                    return;
                }

                int totalVotes = plugin.getDatabaseManager().getTotalVotes(playerUUID);

                String prefix = localeManager.getLocaleMessage("prefix");
                String message = prefix + localeManager.getLocaleMessage("showvotes-message")
                        .replace("{player}", targetPlayer.getName() != null ? targetPlayer.getName() : playerName)
                        .replace("{votes}", String.valueOf(totalVotes));
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
