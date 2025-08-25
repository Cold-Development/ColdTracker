package dev.padrewin.coldtracker.manager;

import dev.padrewin.colddev.ColdPlugin;
import dev.padrewin.colddev.manager.Manager;
import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.discord.DiscordWebhookClient;
import dev.padrewin.coldtracker.setting.SettingKey;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class FlexibleSchedulerManager extends Manager {

    private BukkitTask scheduledTask;
    private final ColdTracker plugin;

    public FlexibleSchedulerManager(ColdPlugin coldPlugin) {
        super(coldPlugin);
        this.plugin = (ColdTracker) coldPlugin;
    }

    @Override
    public void reload() {
        // Cancel existing task
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel();
        }

        // Check if scheduled reports are enabled
        if (!SettingKey.SCHEDULED_REPORTS_ENABLED.get()) {
            if (SettingKey.DEBUG.get()) {
                plugin.getLogger().info("[DEBUG] Scheduled reports are disabled.");
            }
            return;
        }

        // Validate webhook URL
        String webhookUrl = SettingKey.DISCORD_WEBHOOK_URL.get();
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) {
            if (SettingKey.DEBUG.get()) {
                plugin.getLogger().warning("[DEBUG] Discord webhook URL is not configured. Scheduled reports disabled.");
            }
            return;
        }

        // Schedule the next report based on schedule type
        scheduleNextReport();
    }

    @Override
    public void disable() {
        if (scheduledTask != null && !scheduledTask.isCancelled()) {
            scheduledTask.cancel();
        }
    }

    private void scheduleNextReport() {
        long ticksUntilNextReport = calculateTicksUntilNextReport();

        long minutesUntil = ticksUntilNextReport / 20 / 60;
        long hoursUntil = minutesUntil / 60;
        long daysUntil = hoursUntil / 24;

        String scheduleInfo = "";
        if (daysUntil > 0) {
            scheduleInfo += daysUntil + " days, ";
        }
        if (hoursUntil % 24 > 0) {
            scheduleInfo += (hoursUntil % 24) + " hours, ";
        }
        scheduleInfo += (minutesUntil % 60) + " minutes";

        if (SettingKey.DEBUG.get()) {
            plugin.getLogger().info("[DEBUG] Next scheduled report (" + SettingKey.SCHEDULE_TYPE.get().toLowerCase() +
                    ") will run in " + scheduleInfo + ".");
        }

        scheduledTask = Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            try {
                executeScheduledReport();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to execute scheduled report: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // Schedule the next report
                scheduleNextReport();
            }
        }, ticksUntilNextReport);
    }

    private long calculateTicksUntilNextReport() {
        String scheduleType = SettingKey.SCHEDULE_TYPE.get().toLowerCase();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextRun;

        switch (scheduleType) {
            case "daily":
                nextRun = calculateDailyNextRun(now);
                break;
            case "weekly":
                nextRun = calculateWeeklyNextRun(now);
                break;
            case "monthly":
                nextRun = calculateMonthlyNextRun(now);
                break;
            case "interval":
                nextRun = calculateIntervalNextRun(now);
                break;
            default:
                if (SettingKey.DEBUG.get()) {
                    plugin.getLogger().warning("[DEBUG] Invalid schedule type: " + scheduleType + ". Defaulting to daily at midnight.");
                }
                nextRun = now.plusDays(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
                break;
        }

        long secondsUntilNextRun = ChronoUnit.SECONDS.between(now, nextRun);
        return secondsUntilNextRun * 20; // Convert to ticks (20 ticks = 1 second)
    }

    private LocalDateTime calculateDailyNextRun(LocalDateTime now) {
        String dailyTime = SettingKey.DAILY_TIME.get(); // Format: "HH:mm"
        String[] timeParts = dailyTime.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        LocalDateTime nextRun = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return nextRun;
    }

    private LocalDateTime calculateWeeklyNextRun(LocalDateTime now) {
        String weeklyDay = SettingKey.WEEKLY_DAY.get().toUpperCase(); // MONDAY, TUESDAY, etc.
        String weeklyTime = SettingKey.WEEKLY_TIME.get(); // Format: "HH:mm"
        String[] weeklyTimeParts = weeklyTime.split(":");
        int weeklyHour = Integer.parseInt(weeklyTimeParts[0]);
        int weeklyMinute = Integer.parseInt(weeklyTimeParts[1]);

        DayOfWeek targetDay;
        try {
            targetDay = DayOfWeek.valueOf(weeklyDay);
        } catch (IllegalArgumentException e) {
            if (SettingKey.DEBUG.get()) {
                plugin.getLogger().warning("[DEBUG] Invalid weekly day: " + weeklyDay + ". Defaulting to SUNDAY.");
            }
            targetDay = DayOfWeek.SUNDAY;
        }

        LocalDateTime nextRun = now.withHour(weeklyHour).withMinute(weeklyMinute).withSecond(0).withNano(0);

        // Find next occurrence of the specified day
        while (nextRun.getDayOfWeek() != targetDay || nextRun.isBefore(now) || nextRun.isEqual(now)) {
            nextRun = nextRun.plusDays(1);
        }
        return nextRun;
    }

    private LocalDateTime calculateMonthlyNextRun(LocalDateTime now) {
        int targetDay = SettingKey.MONTHLY_DAY.get();
        String timeStr = SettingKey.MONTHLY_TIME.get(); // Format: "HH:mm"
        String[] timeParts = timeStr.split(":");
        int hour = Integer.parseInt(timeParts[0]);
        int minute = Integer.parseInt(timeParts[1]);

        // Start with this month
        YearMonth currentMonth = YearMonth.from(now);
        LocalDateTime nextRun = currentMonth.atDay(Math.min(targetDay, currentMonth.lengthOfMonth()))
                .atTime(hour, minute, 0);

        // If the target time has already passed this month, schedule for next month
        if (nextRun.isBefore(now) || nextRun.isEqual(now)) {
            YearMonth nextMonth = currentMonth.plusMonths(1);
            nextRun = nextMonth.atDay(Math.min(targetDay, nextMonth.lengthOfMonth()))
                    .atTime(hour, minute, 0);
        }
        return nextRun;
    }

    private LocalDateTime calculateIntervalNextRun(LocalDateTime now) {
        int intervalMinutes = SettingKey.INTERVAL_MINUTES.get();
        return now.plusMinutes(intervalMinutes);
    }

    private void executeScheduledReport() {
        String scheduleType = SettingKey.SCHEDULE_TYPE.get().toLowerCase();
        LocalDateTime now = LocalDateTime.now();

        if (SettingKey.DEBUG.get()) {
            plugin.getLogger().info("[DEBUG] Executing " + scheduleType + " staff activity report...");
        }

        try {
            // Execute export command to generate the export file
            executeExportCommand();

            // Check if the export file exists
            String folderName = SettingKey.FOLDER_NAME.get();
            File folder = new File(plugin.getDataFolder(), folderName);

            if (!folder.exists()) {
                plugin.getLogger().severe("Export folder does not exist: " + folder.getPath());
                return;
            }

            // Find the export file using the same format as ExportCommand
            LocalDateTime exportTime = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm");
            String formattedDateTime = exportTime.format(formatter);
            String prefix = SettingKey.FILE_PREFIX.get();

            File exportFile = new File(folder, prefix + formattedDateTime + ".yml");

            // Se așteaptă puțin pentru a permite comenzii să termine de scris fișierul
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            if (!exportFile.exists()) {
                // Poate fișierul a fost creat cu un alt nume, așa că căutăm cel mai recent fișier
                File[] files = folder.listFiles();
                if (files == null || files.length == 0) {
                    plugin.getLogger().severe("No export files found in folder: " + folder.getPath());
                    return;
                }

                // Get the most recent file
                exportFile = null;
                long lastModified = 0;
                for (File file : files) {
                    if (file.isFile() && file.lastModified() > lastModified) {
                        lastModified = file.lastModified();
                        exportFile = file;
                    }
                }
            }

            if (exportFile == null || !exportFile.exists()) {
                plugin.getLogger().severe("Could not find export file after running export command");
                return;
            }

            if (SettingKey.DEBUG.get()) {
                plugin.getLogger().info("[DEBUG] Found export file: " + exportFile.getName());
            }
            try {
                // Read the export file content
                String reportContent = new String(Files.readAllBytes(exportFile.toPath()), StandardCharsets.UTF_8);

                // Send to Discord
                String webhookUrl = SettingKey.DISCORD_WEBHOOK_URL.get();
                DiscordWebhookClient webhookClient = new DiscordWebhookClient(webhookUrl);

                String message = generateDiscordMessage(scheduleType, now);
                String fileName = generateFileName(scheduleType, now);

                webhookClient.sendMessageWithFile(message, fileName, reportContent);
                if (SettingKey.DEBUG.get()) {
                    plugin.getLogger().info("[DEBUG] Scheduled " + scheduleType + " staff activity report sent to Discord successfully.");
                }

                // Wipe database if configured
                if (SettingKey.AUTO_WIPE_AFTER_SEND.get()) {
                    plugin.getDatabaseManager().wipeDatabaseTables();

                    // Also clear real-time cache since database was wiped
                    if (plugin.getPlayerTrackingListener() != null) {
                        plugin.getPlayerTrackingListener().clearCacheAfterWipe();
                    }

                    // Nu mai notificăm jucătorii despre resetarea bazei de date

                    if (SettingKey.DEBUG.get()) {
                        plugin.getLogger().info("[DEBUG] Database wiped after sending " + scheduleType + " report. Starting fresh tracking.");
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to process export file or send to Discord: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error during " + scheduleType + " report execution: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void executeExportCommand() {
        CompletableFuture<Void> future = new CompletableFuture<>();

        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                ConsoleCommandSender console = Bukkit.getConsoleSender();
                Command cmd = Bukkit.getPluginCommand("coldtracker");

                if (cmd != null) {
                    // Execute "coldtracker export confirm" to force export without confirmation
                    boolean success = cmd.execute(console, "coldtracker", new String[]{"export", "confirm"});
                    if (success) {
                        if (SettingKey.DEBUG.get()) {
                            plugin.getLogger().info("[DEBUG] Successfully executed export command");
                        }
                        future.complete(null);
                    } else {
                        plugin.getLogger().warning("Export command execution returned false");
                        future.completeExceptionally(new RuntimeException("Command execution failed"));
                    }
                } else {
                    plugin.getLogger().severe("Could not find coldtracker command!");
                    future.completeExceptionally(new RuntimeException("Command not found"));
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error executing export command: " + e.getMessage());
                future.completeExceptionally(e);
            }
        });

        try {
            // Wait for the command to complete with a timeout
            future.get(30, TimeUnit.SECONDS);

            // Wait an additional moment for the file to be fully written
            Thread.sleep(2000);
        } catch (Exception e) {
            plugin.getLogger().severe("Timeout or error waiting for export command to complete: " + e.getMessage());
        }
    }

    private String generateDiscordMessage(String scheduleType, LocalDateTime now) {
        String baseMessage = SettingKey.DISCORD_MESSAGE.get();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

        return baseMessage
                .replace("{schedule_type}", scheduleType.substring(0, 1).toUpperCase() + scheduleType.substring(1))
                .replace("{date}", now.format(dateFormatter))
                .replace("{time}", now.format(timeFormatter))
                .replace("{month}", now.getMonth().name())
                .replace("{year}", String.valueOf(now.getYear()))
                .replace("{day}", String.valueOf(now.getDayOfMonth()))
                .replace("{weekday}", now.getDayOfWeek().name());
    }

    private String generateFileName(String scheduleType, LocalDateTime now) {
        String prefix = SettingKey.FILE_PREFIX.get();
        DateTimeFormatter formatter;

        switch (scheduleType) {
            case "daily":
                formatter = DateTimeFormatter.ofPattern("MM_dd_yyyy");
                return prefix + "daily_" + now.format(formatter) + ".yml";
            case "weekly":
                formatter = DateTimeFormatter.ofPattern("'week'_w_yyyy");
                return prefix + "weekly_" + now.format(formatter) + ".yml";
            case "monthly":
                formatter = DateTimeFormatter.ofPattern("MM_yyyy");
                return prefix + "monthly_" + now.format(formatter) + ".yml";
            case "interval":
                formatter = DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm");
                return prefix + "interval_" + now.format(formatter) + ".yml";
            default:
                formatter = DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm");
                return prefix + scheduleType + "_" + now.format(formatter) + ".yml";
        }
    }
}