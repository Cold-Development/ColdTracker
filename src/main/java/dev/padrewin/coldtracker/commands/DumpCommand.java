package dev.padrewin.coldtracker.commands;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.manager.LocaleManager;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.gist.GitHubGistClient;
import dev.padrewin.coldtracker.setting.SettingKey;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DumpCommand extends BaseCommand {

    public DumpCommand() {
        super("dump", CommandManager.CommandAliases.DUMP);
    }

    @Override
    public void execute(@NotNull ColdTracker plugin, @NotNull CommandSender sender, @NotNull String[] args) {
        LocaleManager localeManager = plugin.getManager(LocaleManager.class);

        if (!plugin.getConfig().getBoolean(SettingKey.GIST_DUMP.getKey(), false)) {
            localeManager.sendMessage(sender, "command-dump-disabled");
            return;
        }

        LuckPerms luckPerms = plugin.getLuckPerms();

        if (!sender.hasPermission("coldtracker.dump")) {
            localeManager.sendMessage(sender, "no-permission");
            return;
        }

        if (args.length == 0) {
            localeManager.sendMessage(sender, "command-dump-warning");
            return;
        }

        if (!args[0].equalsIgnoreCase("confirm") || args.length > 1) {
            localeManager.sendMessage(sender, "command-dump-usage");
            return;
        }

        CompletableFuture.runAsync(() -> {
            List<OfflinePlayer> playersWithPermission = Arrays.stream(Bukkit.getOfflinePlayers())
                    .filter(p -> {
                        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(p.getUniqueId());
                        return userFuture.join()
                                .getCachedData().getPermissionData().checkPermission("coldtracker.tracktime").asBoolean();
                    })
                    .toList();

            StringBuilder gistContent = new StringBuilder();
            List<String> gistHeader = plugin.getConfig().getStringList(SettingKey.GIST_HEADER.getKey());

            if (!gistHeader.isEmpty()) {
                for (String line : gistHeader) {
                    gistContent.append(line).append("\n");
                }
            }

            gistContent.append("\n");

            boolean trackVotes = plugin.getConfig().getBoolean("track-votes", false);

            List<CompletableFuture<String>> futures = new ArrayList<>();

            for (OfflinePlayer player : playersWithPermission) {
                UUID playerUUID = player.getUniqueId();

                CompletableFuture<Long> timeFuture = plugin.getStorageHandler().getTotalTimeAsync(playerUUID);
                CompletableFuture<Integer> votesFuture = plugin.getStorageHandler().getTotalVotesAsync(playerUUID);

                CompletableFuture<String> playerDataFuture = CompletableFuture.allOf(timeFuture, votesFuture).thenApply(v -> {
                    long totalTime = timeFuture.join();
                    int totalVotes = votesFuture.join();

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

                    StringBuilder playerData = new StringBuilder();
                    playerData.append(player.getName()).append(" has played for ").append(timeFormatted);

                    if (trackVotes) {
                        CompletableFuture<User> userFuture = luckPerms.getUserManager().loadUser(playerUUID);
                        if (userFuture.join().getCachedData().getPermissionData().checkPermission("coldtracker.trackvote").asBoolean()) {
                            playerData.append(" and has ")
                                    .append(totalVotes)
                                    .append(" votes");
                        }
                    }

                    playerData.append(".\n");
                    return playerData.toString();
                });

                futures.add(playerDataFuture);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenRun(() -> {
                for (CompletableFuture<String> future : futures) {
                    gistContent.append(future.join());
                }

                HttpURLConnection connection = null;
                try {
                    String gistToken = plugin.getConfig().getString(SettingKey.GIST_TOKEN.getKey());
                    if (gistToken == null || gistToken.isEmpty()) {
                        plugin.getLogger().severe("No GitHub token found in config.yml!");
                        Bukkit.getScheduler().runTask(plugin, () -> localeManager.sendMessage(sender, "command-dump-fail"));
                        return;
                    }

                    GitHubGistClient gistClient = new GitHubGistClient(gistToken);

                    String filePrefix = plugin.getConfig().getString(SettingKey.FILE_PREFIX.getKey(), "staff_activity_");
                    LocalDateTime now = LocalDateTime.now();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM_dd_yyyy_HH_mm");
                    String formattedDateTime = now.format(formatter);
                    String gistFileName = filePrefix + formattedDateTime + ".yml";

                    String gistUrl = gistClient.createGist(gistContent.toString(), gistFileName, false);

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String prefix = localeManager.getLocaleMessage("prefix");
                        String successMessage = prefix + localeManager.getLocaleMessage("command-dump-success").replace("{link}", gistUrl);
                        sender.sendMessage(successMessage);
                    });

                } catch (Exception e) {
                    plugin.getLogger().severe("Failed to post to GitHub Gist: " + e.getMessage());

                    if (e instanceof IOException && connection != null) {
                        try (Scanner scanner = new Scanner(connection.getErrorStream(), StandardCharsets.UTF_8)) {
                            while (scanner.hasNext()) {
                                plugin.getLogger().severe("GitHub Gist API Error: " + scanner.nextLine());
                            }
                        } catch (Exception ex) {
                            plugin.getLogger().severe("Failed to read GitHub Gist API error response: " + ex.getMessage());
                        }
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        String prefix = localeManager.getLocaleMessage("prefix");
                        String failMessage = prefix + localeManager.getLocaleMessage("command-dump-fail");
                        sender.sendMessage(failMessage);
                    });
                }
            });
        });
    }

    @Override
    public List<String> tabComplete(ColdTracker plugin, CommandSender sender, String[] args) {
        return Collections.emptyList();
    }
}