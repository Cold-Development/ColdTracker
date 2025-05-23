package dev.padrewin.coldtracker.commands;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import dev.padrewin.coldtracker.setting.SettingKey;
import dev.padrewin.coldtracker.util.DiscordWebhookUtil;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ExportCommand extends BaseCommand {

    public ExportCommand() {
        super("export", CommandManager.CommandAliases.EXPORT);
    }

    @Override
    public void execute(ColdTracker plugin, CommandSender sender, String[] args) {
        boolean sendToDiscord = args.length == 1 && args[0].equalsIgnoreCase("discord");
        boolean isConfirm = args.length == 1 && args[0].equalsIgnoreCase("confirm");

        // Notify user that export is starting
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);
        String prefix = localeManager.getLocaleMessage("prefix");

        plugin.getScheduler().runTaskAsync(() -> {
            LuckPerms luckPerms = plugin.getLuckPerms();

            // Check Discord settings
            if (sendToDiscord && !SettingKey.SEND_EXPORT_TO_DISCORD.get()) {
                localeManager.sendMessage(sender, "command-export-discord-disabled");
                return;
            }

            String folderName = plugin.getConfig().getString("folder-name", "exported-database");
            File folder = new File(plugin.getDataFolder(), folderName);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm");
            String formattedDateTime = now.format(formatter);

            File file = new File(folder, "staff_activity_" + formattedDateTime + ".yml");

            // Handle file exists check (only for non-Discord exports)
            if (!sendToDiscord && file.exists() && !isConfirm) {
                localeManager.sendMessage(sender, "command-export-warning");
                return;
            }

            // Handle invalid arguments
            if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("confirm") && !args[0].equalsIgnoreCase("discord") && !sendToDiscord && file.exists())) {
                localeManager.sendMessage(sender, "command-export-description");
                return;
            }

            if (file.exists()) {
                file.delete();
            }

            boolean trackVotes = plugin.getConfig().getBoolean("track-votes", false);
            boolean debug = plugin.getConfig().getBoolean("debug", false);

            try {
                // OPTIMIZED APPROACH: Get UUIDs from database first (much faster)
                Set<UUID> playerUUIDs = plugin.getStorageHandler().getAllPlayerUUIDs().join();

                if (debug) {
                    plugin.getLogger().info("[DEBUG] Found " + playerUUIDs.size() + " players in database for export");
                }

                // Process players in batches to avoid overwhelming the system
                List<PlayerExportData> playerDataList = Collections.synchronizedList(new ArrayList<>());
                List<UUID> uuidList = new ArrayList<>(playerUUIDs);
                int batchSize = 50; // Process 50 players at a time

                for (int i = 0; i < uuidList.size(); i += batchSize) {
                    int endIndex = Math.min(i + batchSize, uuidList.size());
                    List<UUID> batch = uuidList.subList(i, endIndex);

                    if (debug) {
                        plugin.getLogger().info("[DEBUG] Processing batch " + (i/batchSize + 1) + " (" + batch.size() + " players)");
                    }

                    List<CompletableFuture<PlayerExportData>> batchFutures = new ArrayList<>();

                    for (UUID playerUUID : batch) {
                        CompletableFuture<PlayerExportData> future = processPlayerData(plugin, luckPerms, playerUUID, trackVotes);
                        batchFutures.add(future);
                    }

                    // Wait for this batch to complete before starting the next
                    try {
                        CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();

                        // Collect results from this batch
                        for (CompletableFuture<PlayerExportData> future : batchFutures) {
                            try {
                                PlayerExportData data = future.join();
                                if (data != null) {
                                    playerDataList.add(data);
                                }
                            } catch (CompletionException e) {
                                if (debug) {
                                    plugin.getLogger().warning("[DEBUG] Failed to process player in export: " + e.getMessage());
                                }
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().severe("Error processing export batch: " + e.getMessage());
                        if (debug) {
                            e.printStackTrace();
                        }
                    }
                }

                if (debug) {
                    plugin.getLogger().info("[DEBUG] Successfully processed " + playerDataList.size() + " players for export");
                }

                // Write the file
                writeExportFile(plugin, file, playerDataList, trackVotes);

                // Handle Discord or local success
                if (sendToDiscord) {
                    String webhookUrl = SettingKey.DISCORD_WEBHOOK.get();
                    boolean success = DiscordWebhookUtil.sendFileToDiscord(webhookUrl, file);
                    if (!success) {
                        localeManager.sendMessage(sender, "command-export-discord-fail");
                    } else {
                        localeManager.sendMessage(sender, "command-export-discord-success");
                    }
                } else {
                    String successMessage = prefix + localeManager.getLocaleMessage("command-export-success")
                            .replace("{folder}", folderName);
                    sender.sendMessage(successMessage);
                }

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to complete export: " + e.getMessage());
                if (plugin.getConfig().getBoolean("debug", false)) {
                    e.printStackTrace();
                }
                String failMessage = prefix + localeManager.getLocaleMessage("command-export-fail");
                sender.sendMessage(failMessage);
            }
        });
    }

    /**
     * Process individual player data asynchronously
     */
    private CompletableFuture<PlayerExportData> processPlayerData(ColdTracker plugin, LuckPerms luckPerms, UUID playerUUID, boolean trackVotes) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
                if (player.getName() == null) return null;

                // Load user from LuckPerms
                User user = luckPerms.getUserManager().loadUser(playerUUID).join();
                if (user == null || !user.getCachedData().getPermissionData().checkPermission("coldtracker.tracktime").asBoolean()) {
                    return null;
                }

                // Get player data
                long totalTime = plugin.getStorageHandler().getTotalTimeAsync(playerUUID).join();
                int totalVotes = trackVotes ? plugin.getStorageHandler().getTotalVotesAsync(playerUUID).join() : 0;
                boolean canTrackVotes = user.getCachedData().getPermissionData().checkPermission("coldtracker.trackvote").asBoolean();

                return new PlayerExportData(player.getName(), totalTime, totalVotes, canTrackVotes);

            } catch (Exception e) {
                boolean debug = plugin.getConfig().getBoolean("debug", false);
                if (debug) {
                    plugin.getLogger().warning("[DEBUG] Failed to process player " + playerUUID + " for export: " + e.getMessage());
                }
                return null;
            }
        });
    }

    /**
     * Write the export file
     */
    private void writeExportFile(ColdTracker plugin, File file, List<PlayerExportData> playerDataList, boolean trackVotes) throws IOException {
        try (FileWriter writer = new FileWriter(file)) {
            // Write header
            List<String> gistHeader = plugin.getConfig().getStringList(SettingKey.GIST_HEADER.getKey());
            for (String line : gistHeader) {
                writer.write(line + "\n");
            }

            // Write player data
            for (PlayerExportData playerData : playerDataList) {
                long totalSeconds = playerData.totalTime / 1000;
                long days = totalSeconds / 86400;
                long remaining = totalSeconds % 86400;
                long hours = remaining / 3600;
                remaining %= 3600;
                long minutes = remaining / 60;
                long seconds = remaining % 60;

                StringBuilder sbTime = new StringBuilder();
                if (days > 0) {
                    sbTime.append(days).append("d ");
                }
                if (hours > 0) {
                    sbTime.append(hours).append("h ");
                }
                if (minutes > 0) {
                    sbTime.append(minutes).append("m ");
                }
                if (seconds > 0 || (days == 0 && hours == 0 && minutes == 0)) {
                    sbTime.append(seconds).append("s");
                }

                String timeFormatted = sbTime.toString().trim();

                StringBuilder exportLine = new StringBuilder();
                exportLine.append(playerData.playerName)
                        .append(" has a total time of ")
                        .append(timeFormatted);

                if (trackVotes && playerData.canTrackVotes) {
                    exportLine.append(" and has ")
                            .append(playerData.totalVotes)
                            .append(" votes");
                }

                exportLine.append(".\n");
                writer.write(exportLine.toString());
            }

            writer.flush();
        }
    }

    @Override
    public List<String> tabComplete(ColdTracker plugin, CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("confirm");
            if (SettingKey.SEND_EXPORT_TO_DISCORD.get()) {
                completions.add("discord");
            }
            return completions;
        }
        return Collections.emptyList();
    }

    // Helper class to store player export data
    private static class PlayerExportData {
        final String playerName;
        final long totalTime;
        final int totalVotes;
        final boolean canTrackVotes;

        PlayerExportData(String playerName, long totalTime, int totalVotes, boolean canTrackVotes) {
            this.playerName = playerName;
            this.totalTime = totalTime;
            this.totalVotes = totalVotes;
            this.canTrackVotes = canTrackVotes;
        }
    }
}