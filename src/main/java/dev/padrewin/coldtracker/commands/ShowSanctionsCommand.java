package dev.padrewin.coldtracker.commands;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.integration.SanctionCounts;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import dev.padrewin.coldtracker.setting.SettingKey;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ShowSanctionsCommand extends BaseCommand {

    public ShowSanctionsCommand() {
        super("showsanctions", CommandManager.CommandAliases.SHOWSANCTIONS);
    }

    @Override
    public void execute(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);

        if (plugin.getLiteBansHook() == null || !plugin.getLiteBansHook().isAvailable()) {
            localeManager.sendMessage(sender, "command-sanctions-not-available");
            return;
        }

        if (!plugin.getConfig().getBoolean(SettingKey.TRACK_SANCTIONS.getKey(), false)) {
            localeManager.sendMessage(sender, "command-showsanctions-disabled");
            return;
        }

        if (!sender.hasPermission("coldtracker.showsanctions")) {
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
            if (user == null) {
                sender.sendMessage(localeManager.getLocaleMessage("player-not-found").replace("{player}", playerName));
                return;
            }

            boolean hasPermission = user.getCachedData().getPermissionData().checkPermission("coldtracker.tracktime").asBoolean();
            if (!hasPermission) {
                String prefix = localeManager.getLocaleMessage("prefix");
                String message = prefix + localeManager.getLocaleMessage("no-staff-member").replace("{player}", playerName);
                sender.sendMessage(message);
                return;
            }

            String resolvedName = targetPlayer.getName() != null ? targetPlayer.getName() : playerName;

            plugin.getDatabaseManager().getSanctionsPeriodStartAsync()
                    .thenCompose(periodStart -> plugin.getLiteBansHook().getSanctionCountsAsync(playerUUID, periodStart))
                    .thenAccept(counts -> {
                String prefix = localeManager.getLocaleMessage("prefix");

                StringBuilder message = new StringBuilder();
                message.append(" \n");
                message.append(prefix).append(
                        localeManager.getLocaleMessage("command-showsanctions-title").replace("{player}", resolvedName)
                ).append("\n");
                appendSanctionsBlock(message, localeManager, prefix, counts);
                message.append(" \n");

                for (String line : message.toString().split("\n")) {
                    sender.sendMessage(line.isEmpty() ? " " : line);
                }
            });
        });
    }

    static void appendSanctionsBlock(StringBuilder message, LocaleManager localeManager, String prefix, SanctionCounts counts) {
        message.append(prefix).append(
                localeManager.getLocaleMessage("command-stats-sanctions-title").replace("{total}", String.valueOf(counts.total()))
        ).append("\n");
        message.append(prefix).append(
                localeManager.getLocaleMessage("command-stats-sanctions-mute").replace("{count}", String.valueOf(counts.mutes()))
        ).append("\n");
        message.append(prefix).append(
                localeManager.getLocaleMessage("command-stats-sanctions-ban").replace("{count}", String.valueOf(counts.bans()))
        ).append("\n");
        message.append(prefix).append(
                localeManager.getLocaleMessage("command-stats-sanctions-kick").replace("{count}", String.valueOf(counts.kicks()))
        ).append("\n");
        message.append(prefix).append(
                localeManager.getLocaleMessage("command-stats-sanctions-warn").replace("{count}", String.valueOf(counts.warnings()))
        ).append("\n");
    }

    @Override
    public List<String> tabComplete(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return null;
        }
        return Collections.emptyList();
    }
}
