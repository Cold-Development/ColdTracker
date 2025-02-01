package dev.padrewin.coldtracker.database;

import dev.padrewin.coldtracker.ColdTracker;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static dev.padrewin.colddev.manager.AbstractDataManager.*;

public class DatabaseManager {

    private final ColdTracker plugin;
    private Connection connection;

    public DatabaseManager(ColdTracker plugin, String s) {
        this.plugin = plugin;
        connect();
        createTables();
        startBatchUpdater();
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

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlaytimeTable);
            stmt.execute(createSessionsTable);
            stmt.execute(createVotesTable);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables!");
            e.printStackTrace();
        }
    }

    public CompletableFuture<Long> getTotalTimeAsync(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
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
        return CompletableFuture.supplyAsync(() -> {
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
        CompletableFuture.runAsync(() -> {
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
        });
    }

    public CompletableFuture<Void> removeJoinTimeAsync(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            String query = "SELECT join_time FROM staff_sessions WHERE player_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    long joinTime = rs.getLong("join_time");
                    long sessionTime = System.currentTimeMillis() - joinTime;
                    addPlaySession(playerUUID, sessionTime);
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
        CompletableFuture.runAsync(() -> {
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
    }

    public void wipeDatabaseTables() {
        String wipeTimeQuery = "DELETE FROM staff_time";
        String wipeVotesQuery = "DELETE FROM staff_votes";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(wipeTimeQuery);
            stmt.executeUpdate(wipeVotesQuery);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Wiped data from 'staff_time' and 'staff_votes' tables.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to wipe data from database tables!");
            e.printStackTrace();
        }
    }

    private void startBatchUpdater() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            String query = "SELECT player_uuid, join_time FROM staff_sessions";

            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    long joinTime = rs.getLong("join_time");
                    long sessionTime = System.currentTimeMillis() - joinTime;

                    addPlaySession(playerUUID, sessionTime);

                    logJoinTime(playerUUID, Bukkit.getOfflinePlayer(playerUUID).getName(), System.currentTimeMillis());
                }

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("[DEBUG] Batch playtime update completed.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to execute batch playtime update!");
                e.printStackTrace();
            }
        }, 20 * 60, 20 * 60);
    }

    public void cleanupStaleSessions() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String query = "SELECT player_uuid, join_time FROM staff_sessions";
            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));

                    if (Bukkit.getPlayer(playerUUID) == null) {
                        long joinTime = rs.getLong("join_time");
                        long sessionTime = System.currentTimeMillis() - joinTime;

                        addPlaySession(playerUUID, sessionTime);

                        removeJoinTimeAsync(playerUUID).exceptionally(ex -> {
                            plugin.getLogger().severe("[ERROR] Failed to remove stale session for " + playerUUID + ": " + ex.getMessage());
                            return null;
                        });

                        if (plugin.getConfig().getBoolean("debug", false)) {
                            plugin.getLogger().warning("[DEBUG] Removed stale session for " + playerUUID);
                        }
                    }
                }

            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to clean up stale sessions!");
                e.printStackTrace();
            }
        });
    }


    public void closeConnection() {
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