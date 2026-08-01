package dev.padrewin.coldtracker.database;

import dev.padrewin.coldtracker.ColdTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static dev.padrewin.colddev.manager.AbstractDataManager.*;

public class DatabaseManager {
    private static final long SESSION_UPDATE_INTERVAL_TICKS = 20L * 60L;

    private final ColdTracker plugin;
    private Connection connection;
    private final ExecutorService dbExecutor;
    private final ConcurrentLinkedQueue<CompletableFuture<Void>> pendingVoteWrites = new ConcurrentLinkedQueue<>();

    public DatabaseManager(ColdTracker plugin, String s) {
        this.plugin = plugin;
        this.dbExecutor = Executors.newSingleThreadExecutor(new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "ColdTracker-DB-" + counter.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });

        connect();
        createTables();
        startBatchUpdater();
    }

    private <T> CompletableFuture<T> supplyDbAsync(Supplier<T> task) {
        CompletableFuture<T> future = new CompletableFuture<>();
        dbExecutor.execute(() -> {
            try {
                future.complete(task.get());
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private CompletableFuture<Void> runDbAsync(Runnable task) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        dbExecutor.execute(() -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    private void connect() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            String dbPath = dataFolder.getAbsolutePath() + File.separator + "coldtracker.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            plugin.getLogger().info(ANSI_LIGHT_BLUE + "Database connected using SQLite. " + ANSI_BOLD + ANSI_GREEN + "✔" + ANSI_RESET);
        } catch (SQLException e) {
            plugin.getLogger().warning(ANSI_RED + "Database failed to connect. " + ANSI_BOLD + ANSI_RED + "✘" + ANSI_RESET);
            e.printStackTrace();
        }
    }

    private void createTables() {
        String createPlaytimeTable = "CREATE TABLE IF NOT EXISTS staff_time (" +
                "player_uuid TEXT PRIMARY KEY," +
                "player_name TEXT NOT NULL," +
                "total_time INTEGER NOT NULL DEFAULT 0" +
                ");";

        String createSessionsTable = "CREATE TABLE IF NOT EXISTS staff_sessions (" +
                "player_uuid TEXT PRIMARY KEY," +
                "player_name TEXT NOT NULL," +
                "join_time INTEGER NOT NULL" +
                ");";

        String createVotesTable = "CREATE TABLE IF NOT EXISTS staff_votes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "service_name TEXT NOT NULL," +
                "vote_time TEXT NOT NULL" +
                ");";

        String createSanctionsPeriodTable = "CREATE TABLE IF NOT EXISTS sanctions_period (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                "last_reset INTEGER NOT NULL DEFAULT 0" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlaytimeTable);
            stmt.execute(createSessionsTable);
            stmt.execute(createVotesTable);
            stmt.execute(createSanctionsPeriodTable);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables!");
            e.printStackTrace();
        }

        // On first-ever startup with this feature, start the sanctions clock at "now" instead of
        // the epoch, so upgrading an existing install doesn't surface a server's entire punishment
        // history. Existing playtime/vote data in the other tables is untouched either way.
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT OR IGNORE INTO sanctions_period (id, last_reset) VALUES (1, ?)")) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize sanctions period!");
            e.printStackTrace();
        }
    }

    /**
     * Timestamp (epoch millis) marking the start of the current tracking period for sanctions.
     * LiteBans keeps the full punishment history forever, so instead of deleting anything there,
     * we only remember when the last reset happened and count sanctions issued after that point.
     */
    public CompletableFuture<Long> getSanctionsPeriodStartAsync() {
        return supplyDbAsync(() -> {
            String query = "SELECT last_reset FROM sanctions_period WHERE id = 1";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getLong("last_reset");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get sanctions period start!");
                e.printStackTrace();
            }
            return 0L;
        });
    }

    private void resetSanctionsPeriod() {
        String query = "UPDATE sanctions_period SET last_reset = ? WHERE id = 1";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setLong(1, System.currentTimeMillis());
            stmt.executeUpdate();

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Sanctions tracking period has been reset.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to reset sanctions period!");
            e.printStackTrace();
        }
    }

    public CompletableFuture<Long> getTotalTimeAsync(UUID playerUUID) {
        return supplyDbAsync(() -> {
            String query = "SELECT total_time FROM staff_time WHERE player_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getLong("total_time");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get total time for player " + playerUUID + "!");
                e.printStackTrace();
            }
            return 0L;
        });
    }

    public CompletableFuture<Integer> getTotalVotesAsync(UUID playerUUID) {
        return supplyDbAsync(() -> {
            String query = "SELECT COUNT(*) AS vote_count FROM staff_votes WHERE player_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("vote_count");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get total votes for player " + playerUUID + "!");
                e.printStackTrace();
            }
            return 0;
        });
    }

    public void logJoinTime(UUID playerUUID, String playerName, long joinTime) {
        runDbAsync(() -> updateJoinTime(playerUUID, playerName, joinTime))
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Failed to schedule join time update for " + playerName + ": " + ex.getMessage());
                    return null;
                });
    }

    private void updateJoinTime(UUID playerUUID, String playerName, long joinTime) {
        String query = "INSERT INTO staff_sessions (player_uuid, player_name, join_time) VALUES (?, ?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET join_time = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, playerName);
            stmt.setLong(3, joinTime);
            stmt.setLong(4, joinTime);
            stmt.executeUpdate();

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Logged join time for " + playerName + " at " + joinTime);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log join time for " + playerName + "!");
            e.printStackTrace();
        }
    }

    public CompletableFuture<Void> removeJoinTimeAsync(UUID playerUUID) {
        return runDbAsync(() -> {
            String query = "SELECT join_time FROM staff_sessions WHERE player_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    long joinTime = rs.getLong("join_time");
                    long sessionTime = System.currentTimeMillis() - joinTime;
                    if (sessionTime > 0) {
                        addPlaySession(playerUUID, sessionTime);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to remove join time for " + playerUUID + "!");
                e.printStackTrace();
            }

            String deleteQuery = "DELETE FROM staff_sessions WHERE player_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(deleteQuery)) {
                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to delete join time entry for " + playerUUID + "!");
                e.printStackTrace();
            }
        });
    }

    private String getPlayerNameFromSessions(UUID playerUUID) {
        String query = "SELECT player_name FROM staff_sessions WHERE player_uuid = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("player_name");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to fetch player name for UUID: " + playerUUID);
            e.printStackTrace();
        }
        return null;
    }

    public void addPlaySession(UUID playerUUID, long sessionTime) {
        String query = "INSERT INTO staff_time (player_uuid, player_name, total_time) VALUES (?, ?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET total_time = total_time + ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            String playerName = Bukkit.getOfflinePlayer(playerUUID).getName();

            if (playerName == null) {
                playerName = getPlayerNameFromSessions(playerUUID);
            }

            if (playerName == null) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("[DEBUG] Skipping playtime update for " + playerUUID + " because player_name is missing.");
                }
                return;
            }

            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, playerName);
            stmt.setLong(3, sessionTime);
            stmt.setLong(4, sessionTime);
            stmt.executeUpdate();

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Updated playtime for " + playerName + " (" + playerUUID + ") with " + sessionTime + "ms.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update playtime for " + playerUUID + "!");
            e.printStackTrace();
        }
    }

    public void addVote(UUID playerUUID, String playerName, String serviceName, String timestamp) {
        CompletableFuture<Void> voteWrite = runDbAsync(() -> {
            String query = "INSERT INTO staff_votes (player_uuid, player_name, service_name, vote_time) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.setString(3, serviceName);
                stmt.setString(4, timestamp);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to log vote for player " + playerName + "!");
                e.printStackTrace();
            }
        });
        pendingVoteWrites.add(voteWrite);
        voteWrite.whenComplete((unused, throwable) -> pendingVoteWrites.remove(voteWrite));
    }

    public CompletableFuture<Void> waitForPendingVoteWritesAsync() {
        return CompletableFuture.runAsync(() -> {
            List<CompletableFuture<Void>> snapshot = new ArrayList<>(pendingVoteWrites);
            if (!snapshot.isEmpty()) {
                CompletableFuture.allOf(snapshot.toArray(new CompletableFuture[0])).join();
            }
        });
    }

    public CompletableFuture<Void> flushActiveSessionsAsync() {
        return runDbAsync(() -> {
            long now = System.currentTimeMillis();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("coldtracker.tracktime")) {
                    continue;
                }

                UUID playerUUID = player.getUniqueId();
                String query = "SELECT join_time FROM staff_sessions WHERE player_uuid = ?";
                try (PreparedStatement stmt = connection.prepareStatement(query)) {
                    stmt.setString(1, playerUUID.toString());
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next()) {
                        long joinTime = rs.getLong("join_time");
                        long sessionTime = now - joinTime;
                        if (sessionTime > 0) {
                            addPlaySession(playerUUID, sessionTime);
                            updateJoinTime(playerUUID, player.getName(), now);
                        }
                    } else {
                        updateJoinTime(playerUUID, player.getName(), now);
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to flush active session for " + player.getName() + "!");
                    e.printStackTrace();
                }
            }
        });
    }

    public void wipeDatabaseTables() {
        runDbAsync(() -> {
            String wipeTimeQuery = "DELETE FROM staff_time";
            String wipeVotesQuery = "DELETE FROM staff_votes";

            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(wipeTimeQuery);
                stmt.executeUpdate(wipeVotesQuery);
                resetSanctionsPeriod();

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("[DEBUG] Wiped data from 'staff_time' and 'staff_votes' tables, and reset the sanctions tracking period.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to wipe data from database tables!");
                e.printStackTrace();
            }
        }).join();
    }

    private void startBatchUpdater() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            runDbAsync(() -> {
                String query = "SELECT player_uuid, join_time FROM staff_sessions";

                try (PreparedStatement stmt = connection.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                        long joinTime = rs.getLong("join_time");
                        long sessionTime = System.currentTimeMillis() - joinTime;

                        if (sessionTime > 0) {
                            addPlaySession(playerUUID, sessionTime);
                        }
                        updateJoinTime(playerUUID, Bukkit.getOfflinePlayer(playerUUID).getName(), System.currentTimeMillis());
                    }

                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("[DEBUG] Batch playtime update completed.");
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to execute batch playtime update!");
                    e.printStackTrace();
                }
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Batch DB task failed: " + ex.getMessage());
                return null;
            });
        }, SESSION_UPDATE_INTERVAL_TICKS, SESSION_UPDATE_INTERVAL_TICKS);
    }

    public void cleanupStaleSessions() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            runDbAsync(() -> {
                String query = "SELECT player_uuid FROM staff_sessions";
                try (PreparedStatement stmt = connection.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                        if (Bukkit.getPlayer(playerUUID) == null) {
                            String deleteQuery = "DELETE FROM staff_sessions WHERE player_uuid = ?";
                            try (PreparedStatement deleteStmt = connection.prepareStatement(deleteQuery)) {
                                deleteStmt.setString(1, playerUUID.toString());
                                deleteStmt.executeUpdate();
                            }

                            if (plugin.getConfig().getBoolean("debug", false)) {
                                plugin.getLogger().warning("[DEBUG] Removed stale session for " + playerUUID +
                                        " without adding offline time after restart.");
                            }
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().severe("Failed to clean up stale sessions!");
                    e.printStackTrace();
                }
            }).exceptionally(ex -> {
                plugin.getLogger().severe("Stale-session cleanup DB task failed: " + ex.getMessage());
                return null;
            });
        });
    }

    public void closeConnection() {
        try {
            waitForPendingVoteWritesAsync().join();
        } catch (Exception ignored) {
        }

        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                dbExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            dbExecutor.shutdownNow();
        }

        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close the database connection!");
            e.printStackTrace();
        }
    }
}
