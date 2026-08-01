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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public class ExportCommand extends BaseCommand {
    public static class ExportResult {
        private final File file;
        private final int exportedStaffCount;
        private final long exportedTotalTimeMs;
        private final int exportedTotalVotes;
        private final SanctionCounts exportedSanctionCounts;
        private final boolean hadWriteErrors;

        public ExportResult(File file, int exportedStaffCount, long exportedTotalTimeMs, int exportedTotalVotes, SanctionCounts exportedSanctionCounts, boolean hadWriteErrors) {
            this.file = file;
            this.exportedStaffCount = exportedStaffCount;
            this.exportedTotalTimeMs = exportedTotalTimeMs;
            this.exportedTotalVotes = exportedTotalVotes;
            this.exportedSanctionCounts = exportedSanctionCounts;
            this.hadWriteErrors = hadWriteErrors;
        }

        public File getFile() {
            return file;
        }

        public int getExportedStaffCount() {
            return exportedStaffCount;
        }

        public long getExportedTotalTimeMs() {
            return exportedTotalTimeMs;
        }

        public int getExportedTotalVotes() {
            return exportedTotalVotes;
        }

        public SanctionCounts getExportedSanctionCounts() {
            return exportedSanctionCounts;
        }

        public boolean hadWriteErrors() {
            return hadWriteErrors;
        }
    }

    private static final AtomicReference<CompletableFuture<File>> LAST_EXPORT_FUTURE =
            new AtomicReference<>(CompletableFuture.completedFuture(null));
    private static final AtomicReference<CompletableFuture<ExportResult>> LAST_EXPORT_RESULT_FUTURE =
            new AtomicReference<>(CompletableFuture.completedFuture(null));

    public static CompletableFuture<File> getLastExportFuture() {
        return LAST_EXPORT_FUTURE.get();
    }

    public static CompletableFuture<ExportResult> getLastExportResultFuture() {
        return LAST_EXPORT_RESULT_FUTURE.get();
    }

    public ExportCommand() {
        super("export", CommandManager.CommandAliases.EXPORT);
    }

    @Override
    public void execute(ColdTracker plugin, CommandSender sender, String[] args) {
        CompletableFuture<File> exportFuture = new CompletableFuture<>();
        CompletableFuture<ExportResult> exportResultFuture = new CompletableFuture<>();
        LAST_EXPORT_FUTURE.set(exportFuture);
        LAST_EXPORT_RESULT_FUTURE.set(exportResultFuture);

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

            String filePrefix = plugin.getConfig().getString(SettingKey.FILE_PREFIX.getKey(), "staff_activity_");
            File file = new File(folder, filePrefix + formattedDateTime + ".yml");

            if (file.exists() && (args.length == 0 || !args[0].equalsIgnoreCase("confirm"))) {
                localeManager.sendMessage(sender, "command-export-warning");
                exportFuture.complete(file);
                exportResultFuture.complete(new ExportResult(file, 0, 0L, 0, SanctionCounts.EMPTY, false));
                return;
            }

            if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("confirm") && file.exists())) {
                localeManager.sendMessage(sender, "command-export-description");
                exportFuture.complete(file);
                exportResultFuture.complete(new ExportResult(file, 0, 0L, 0, SanctionCounts.EMPTY, false));
                return;
            }

            if (file.exists()) {
                file.delete();
            }

            boolean trackVotes = plugin.getConfig().getBoolean(SettingKey.TRACK_VOTES.getKey(), false);
            boolean trackSanctions = plugin.getConfig().getBoolean(SettingKey.TRACK_SANCTIONS.getKey(), false)
                    && plugin.getLiteBansHook() != null && plugin.getLiteBansHook().isAvailable();

            try (FileWriter writer = new FileWriter(file)) {
                plugin.getDatabaseManager().flushActiveSessionsAsync().join();
                plugin.getDatabaseManager().waitForPendingVoteWritesAsync().join();

                long sanctionsPeriodStart = trackSanctions ? plugin.getDatabaseManager().getSanctionsPeriodStartAsync().join() : 0L;

                final int[] exportedStaffCount = {0};
                final long[] exportedTotalTimeMs = {0L};
                final int[] exportedTotalVotes = {0};
                final int[] exportedTotalMutes = {0};
                final int[] exportedTotalBans = {0};
                final int[] exportedTotalKicks = {0};
                final int[] exportedTotalWarnings = {0};
                final boolean[] hadWriteErrors = {false};

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
                            CompletableFuture<SanctionCounts> sanctionsFuture = trackSanctions
                                    ? plugin.getLiteBansHook().getSanctionCountsAsync(playerUUID, sanctionsPeriodStart)
                                    : CompletableFuture.completedFuture(SanctionCounts.EMPTY);

                            CompletableFuture.allOf(timeFuture, votesFuture, sanctionsFuture).thenRun(() -> {
                                long totalTime = timeFuture.join();
                                int totalVotes = votesFuture.join();
                                SanctionCounts sanctionCounts = sanctionsFuture.join();

                                long totalSeconds = totalTime / 1000;

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
                                exportLine.append(player.getName())
                                        .append(" has a total time of ")
                                        .append(timeFormatted);

                                if (trackVotes) {
                                    exportLine.append(" and has ")
                                            .append(totalVotes)
                                            .append(" votes");
                                }

                                if (trackSanctions) {
                                    exportLine.append(" and has issued ")
                                            .append(sanctionCounts.total())
                                            .append(" sanctions (Mute: ").append(sanctionCounts.mutes())
                                            .append(", Ban: ").append(sanctionCounts.bans())
                                            .append(", Kick: ").append(sanctionCounts.kicks())
                                            .append(", Warn: ").append(sanctionCounts.warnings())
                                            .append(")");
                                }

                                exportLine.append(".\n");

                                try {
                                    writer.write(exportLine.toString());
                                    exportedStaffCount[0]++;
                                    exportedTotalTimeMs[0] += totalTime;
                                    if (trackVotes) {
                                        exportedTotalVotes[0] += totalVotes;
                                    }
                                    if (trackSanctions) {
                                        exportedTotalMutes[0] += sanctionCounts.mutes();
                                        exportedTotalBans[0] += sanctionCounts.bans();
                                        exportedTotalKicks[0] += sanctionCounts.kicks();
                                        exportedTotalWarnings[0] += sanctionCounts.warnings();
                                    }
                                } catch (IOException e) {
                                    plugin.getLogger().severe("Failed to write player data to " + file.getName() + ": " + e.getMessage());
                                    hadWriteErrors[0] = true;
                                }
                            }).join();
                        }
                    }).join();
                }

                String prefix = localeManager.getLocaleMessage("prefix");
                String successMessage = prefix + localeManager.getLocaleMessage("command-export-success")
                        .replace("{folder}", folderName);
                sender.sendMessage(successMessage);
                exportFuture.complete(file);
                exportResultFuture.complete(new ExportResult(
                        file,
                        exportedStaffCount[0],
                        exportedTotalTimeMs[0],
                        exportedTotalVotes[0],
                        new SanctionCounts(exportedTotalMutes[0], exportedTotalBans[0], exportedTotalKicks[0], exportedTotalWarnings[0]),
                        hadWriteErrors[0]
                ));

            } catch (IOException e) {
                plugin.getLogger().severe("Failed to create " + file.getName() + ": " + e.getMessage());

                String prefix = localeManager.getLocaleMessage("prefix");
                String failMessage = prefix + localeManager.getLocaleMessage("command-export-fail");
                sender.sendMessage(failMessage);
                exportFuture.completeExceptionally(e);
                exportResultFuture.completeExceptionally(e);
            }
        });
    }

    @Override
    public List<String> tabComplete(ColdTracker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}
