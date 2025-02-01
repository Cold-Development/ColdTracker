package dev.padrewin.coldtracker.commands;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import dev.padrewin.coldtracker.setting.SettingKey;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ExportCommand extends BaseCommand {

    public ExportCommand() {
        super("export", CommandManager.CommandAliases.EXPORT);
    }

    @Override
    public void execute(ColdTracker plugin, CommandSender sender, String[] args) {
        plugin.getScheduler().runTaskAsync(() -> {
            LocaleManager localeManager = plugin.getManager(LocaleManager.class);
            LuckPerms luckPerms = plugin.getLuckPerms();

            String folderName = plugin.getConfig().getString("folder-name", "exported-database");
            File folder = new File(plugin.getDataFolder(), folderName);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm");
            String formattedDateTime = now.format(formatter);

            File file = new File(folder, "staff_activity_" + formattedDateTime + ".yml");

            if (file.exists() && (args.length == 0 || !args[0].equalsIgnoreCase("confirm"))) {
                localeManager.sendMessage(sender, "command-export-warning");
                return;
            }

            if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("confirm") && file.exists())) {
                localeManager.sendMessage(sender, "command-export-description");
                return;
            }

            if (file.exists()) {
                file.delete();
            }

            boolean trackVotes = plugin.getConfig().getBoolean("track-votes", false);

            try (FileWriter writer = new FileWriter(file)) {
                List<String> gistHeader = plugin.getConfig().getStringList(SettingKey.GIST_HEADER.getKey());
                for (String line : gistHeader) {
                    writer.write(line + "\n");
                }

                for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                    CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(player.getUniqueId());
                    userFuture.thenAccept(user -> {
                        if (user != null && user.getCachedData().getPermissionData().checkPermission("coldtracker.tracktime").asBoolean()) {
                            UUID playerUUID = player.getUniqueId();

                            CompletableFuture<Long> timeFuture = plugin.getDatabaseManager().getTotalTimeAsync(playerUUID);
                            CompletableFuture<Integer> votesFuture = plugin.getDatabaseManager().getTotalVotesAsync(playerUUID);

                            CompletableFuture.allOf(timeFuture, votesFuture).thenRun(() -> {
                                long totalTime = timeFuture.join();
                                int totalVotes = votesFuture.join();

                                long hours = (totalTime / 1000) / 3600;
                                long minutes = ((totalTime / 1000) % 3600) / 60;
                                long seconds = (totalTime / 1000) % 60;
                                long days = hours / 24;
                                hours = hours % 24;
                                String timeFormatted = String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);

                                StringBuilder exportLine = new StringBuilder();
                                exportLine.append(player.getName()).append(" has a total time of ").append(timeFormatted);

                                if (trackVotes && user.getCachedData().getPermissionData().checkPermission("coldtracker.trackvote").asBoolean()) {
                                    exportLine.append(" and ").append(totalVotes).append(" votes");
                                }

                                exportLine.append(".\n");

                                try {
                                    writer.write(exportLine.toString());
                                } catch (IOException e) {
                                    plugin.getLogger().severe("Failed to write player data to " + file.getName() + ": " + e.getMessage());
                                }
                            }).join();
                        }
                    }).join();
                }

                String prefix = localeManager.getLocaleMessage("prefix");
                String successMessage = prefix + localeManager.getLocaleMessage("command-export-success")
                        .replace("{folder}", folderName);
                sender.sendMessage(successMessage);

            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create " + file.getName() + ": " + e.getMessage());

                String prefix = localeManager.getLocaleMessage("prefix");
                String failMessage = prefix + localeManager.getLocaleMessage("command-export-fail");
                sender.sendMessage(failMessage);
            }
        });
    }

    @Override
    public List<String> tabComplete(ColdTracker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}