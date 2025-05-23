package dev.padrewin.coldtracker.rotation;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.setting.SettingKey;
import dev.padrewin.coldtracker.util.DiscordWebhookUtil;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class RotationScheduler {

    private final ColdTracker plugin;

    public RotationScheduler(ColdTracker plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!SettingKey.ROTATION_ENABLED.get()) return;

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (shouldRunRotation()) {
                runRotation();
            }
        }, 20L * 60 * 60 * 24, 20L * 60 * 60 * 24); // Once per day
    }

    private boolean shouldRunRotation() {
        LocalDateTime now = LocalDateTime.now();
        String freq = SettingKey.ROTATION_FREQUENCY.get().toLowerCase();
        int startDay = SettingKey.ROTATION_START_DAY.get();

        if (freq.equals("monthly")) {
            return now.getDayOfMonth() == startDay;
        } else if (freq.equals("weekly")) {
            return now.getDayOfWeek().getValue() == startDay;
        }

        return false;
    }

    private void runRotation() {
        boolean debug = SettingKey.DEBUG.get();
        if (debug) plugin.getLogger().info("[Rotation] Starting export...");

        plugin.getScheduler().runTaskAsync(() -> {
            File exportFile = createRotationExportFile();
            if (exportFile == null) {
                plugin.getLogger().severe("[Rotation] Failed to create rotation export file!");
                return;
            }

            boolean success = false;

            // Save locally if enabled
            if (SettingKey.ROTATION_SAVE_LOCAL.get()) {
                if (debug) plugin.getLogger().info("[Rotation] Export saved locally: " + exportFile.getName());
                success = true;
            }

            // Send to Discord if enabled
            if (SettingKey.ROTATION_SEND_TO_DISCORD.get()) {
                String webhookUrl = SettingKey.DISCORD_WEBHOOK.get();
                if (webhookUrl != null && !webhookUrl.isEmpty()) {
                    if (debug) plugin.getLogger().info("[Rotation] Sending rotation export to Discord...");
                    boolean discordSuccess = DiscordWebhookUtil.sendFileToDiscord(webhookUrl, exportFile);

                    if (discordSuccess) {
                        if (debug) plugin.getLogger().info("[Rotation] Successfully sent rotation export to Discord!");
                        success = true;
                    } else {
                        plugin.getLogger().severe("[Rotation] Failed to send rotation export to Discord!");
                    }
                } else {
                    if (debug) plugin.getLogger().warning("[Rotation] Discord webhook URL not configured!");
                }
            }

            // Clean up file if not saving locally
            if (!SettingKey.ROTATION_SAVE_LOCAL.get() && exportFile.exists()) {
                exportFile.delete();
                if (debug) plugin.getLogger().info("[Rotation] Temporary export file cleaned up.");
            }

            // Clean database if enabled and export was successful
            if (success && SettingKey.ROTATION_CLEAN_AFTER.get()) {
                if (debug) plugin.getLogger().info("[Rotation] Cleaning database after successful export...");
                plugin.getStorageHandler().wipeDatabaseTables();
                if (debug) plugin.getLogger().info("[Rotation] Database cleaned successfully!");
            }

            if (debug) plugin.getLogger().info("[Rotation] Export complete.");
        });
    }

    /**
     * Creates the rotation export file using the same format as manual exports
     */
    private File createRotationExportFile() {
        try {
            LuckPerms luckPerms = plugin.getLuckPerms();
            String folderName = SettingKey.FOLDER_NAME.get();
            File folder = new File(plugin.getDataFolder(), folderName);
            if (!folder.exists()) {
                folder.mkdirs();
            }

            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm");
            String formattedDateTime = now.format(formatter);

            File file = new File(folder, "rotation_export_" + formattedDateTime + ".yml");

            // Collect all player data first
            List<PlayerExportData> playerDataList = Collections.synchronizedList(new ArrayList<>());
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            // Get all player UUIDs from database
            Set<UUID> playerUUIDs = plugin.getStorageHandler().getAllPlayerUUIDs().join();

            for (UUID playerUUID : playerUUIDs) {
                OfflinePlayer player = Bukkit.getOfflinePlayer(playerUUID);
                if (player.getName() == null) continue; // Skip players with no name

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(playerUUID);
                        User user = userFuture.join();

                        if (user != null && user.getCachedData().getPermissionData().checkPermission("coldtracker.tracktime").asBoolean()) {
                            CompletableFuture<Long> timeFuture = plugin.getStorageHandler().getTotalTimeAsync(playerUUID);
                            CompletableFuture<Integer> votesFuture = plugin.getStorageHandler().getTotalVotesAsync(playerUUID);

                            CompletableFuture.allOf(timeFuture, votesFuture).thenRun(() -> {
                                long totalTime = timeFuture.join();
                                int totalVotes = votesFuture.join();

                                boolean canTrackVotes = user.getCachedData().getPermissionData().checkPermission("coldtracker.trackvote").asBoolean();

                                PlayerExportData data = new PlayerExportData(
                                        player.getName(),
                                        totalTime,
                                        totalVotes,
                                        canTrackVotes
                                );
                                playerDataList.add(data);
                            }).join();
                        }
                    } catch (Exception e) {
                        if (SettingKey.DEBUG.get()) {
                            plugin.getLogger().warning("Failed to process player " + player.getName() + " during rotation: " + e.getMessage());
                        }
                    }
                });
                futures.add(future);
            }

            // Wait for all data collection to complete
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception e) {
                if (SettingKey.DEBUG.get()) {
                    plugin.getLogger().severe("Error collecting player data during rotation: " + e.getMessage());
                }
            }

            // Write the file
            boolean trackVotes = plugin.getConfig().getBoolean("track-votes", false);

            try (FileWriter writer = new FileWriter(file)) {
                // Write header
                List<String> gistHeader = plugin.getConfig().getStringList(SettingKey.GIST_HEADER.getKey());
                for (String line : gistHeader) {
                    writer.write(line + "\n");
                }

                writer.write("# Automatic Rotation Export\n");
                writer.write("# Generated: " + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n\n");

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

            return file;

        } catch (IOException e) {
            plugin.getLogger().severe("[Rotation] Failed to create export file: " + e.getMessage());
            if (SettingKey.DEBUG.get()) {
                e.printStackTrace();
            }
            return null;
        }
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