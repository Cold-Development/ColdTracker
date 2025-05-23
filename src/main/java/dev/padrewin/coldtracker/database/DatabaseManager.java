package dev.padrewin.coldtracker.database;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.util.ServerNameResolver;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DatabaseManager {

    private final ColdTracker plugin;
    private Connection connection;

    public DatabaseManager(ColdTracker plugin, String s) {
        this.plugin = plugin;
        connect();
        createTables();
    }

    private void connect() {
        try {
            File dataFolder = plugin.getDataFolder();
            if (!dataFolder.exists()) {
                dataFolder.mkdirs();
            }

            String dbPath = dataFolder.getAbsolutePath() + File.separator + "coldtracker.db";
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            plugin.getLogger().info("Database connected using SQLite.");
        } catch (SQLException e) {
            plugin.getLogger().warning("Database failed to connect.");
            e.printStackTrace();
        }
    }

    public void reconnect() {
        closeConnection();
        connect();
        plugin.getLogger().info("Database reconnected.");
    }

    private void createTables() {
        String createPlaytimeTable = "CREATE TABLE IF NOT EXISTS staff_time (" +
                "player_uuid TEXT PRIMARY KEY," +
                "player_name TEXT NOT NULL," +
                "total_time INTEGER NOT NULL DEFAULT 0," +
                "server_name TEXT DEFAULT NULL" +
                ");";

        String createSessionsTable = "CREATE TABLE IF NOT EXISTS staff_sessions (" +
                "player_uuid TEXT PRIMARY KEY," +
                "player_name TEXT NOT NULL," +
                "join_time INTEGER NOT NULL," +
                "server_name TEXT DEFAULT NULL" +
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

        // Add server_name columns if they don't exist
        addServerNameColumns();
    }

    private void addServerNameColumns() {
        try (Statement stmt = connection.createStatement()) {
            // Check and add server_name to staff_time
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(staff_time);");
            boolean hasServerNameTime = false;
            while (rs.next()) {
                if ("server_name".equalsIgnoreCase(rs.getString("name"))) {
                    hasServerNameTime = true;
                    break;
                }
            }
            if (!hasServerNameTime) {
                plugin.getLogger().info("Adding missing 'server_name' column to 'staff_time' table...");
                stmt.execute("ALTER TABLE staff_time ADD COLUMN server_name TEXT DEFAULT NULL;");
            }
            rs.close();

            // Check and add server_name to staff_sessions
            rs = stmt.executeQuery("PRAGMA table_info(staff_sessions);");
            boolean hasServerNameSessions = false;
            while (rs.next()) {
                if ("server_name".equalsIgnoreCase(rs.getString("name"))) {
                    hasServerNameSessions = true;
                    break;
                }
            }
            if (!hasServerNameSessions) {
                plugin.getLogger().info("Adding missing 'server_name' column to 'staff_sessions' table...");
                stmt.execute("ALTER TABLE staff_sessions ADD COLUMN server_name TEXT DEFAULT NULL;");
            }
            rs.close();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add 'server_name' columns!");
            e.printStackTrace();
        }
    }

    private String resolveServerName() {
        return ServerNameResolver.resolveServerName();
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
            String query = "INSERT INTO staff_sessions (player_uuid, player_name, join_time, server_name) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET join_time = ?, server_name = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, joinTime);
                stmt.setString(4, resolveServerName());
                stmt.setLong(5, joinTime);
                stmt.setString(6, resolveServerName());
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
        String query = "INSERT INTO staff_time (player_uuid, player_name, total_time, server_name) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET total_time = total_time + ?, server_name = ?";
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
            stmt.setString(4, resolveServerName());
            stmt.setLong(5, sessionTime);
            stmt.setString(6, resolveServerName());
            stmt.executeUpdate();

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Updated playtime for " + playerName + " (" + playerUUID + ") with " + sessionTime + "ms on server " + resolveServerName());
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

    public CompletableFuture<Map<String, Long>> getPlaytimeByServer(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> playtimeMap = new HashMap<>();
            String query = "SELECT server_name, total_time FROM staff_time WHERE player_uuid = ?";
            try (PreparedStatement stmt = connection.prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String server = rs.getString("server_name");
                    long time = rs.getLong("total_time");
                    if (server == null || server.isBlank()) server = "unknown";
                    playtimeMap.put(server, time);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get playtime by server for " + playerUUID);
                e.printStackTrace();
            }
            return playtimeMap;
        });
    }

    public CompletableFuture<Set<UUID>> getAllPlayerUUIDs() {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> uuids = new HashSet<>();
            String query = "SELECT player_uuid FROM staff_time";
            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    uuids.add(UUID.fromString(rs.getString("player_uuid")));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get all player UUIDs from SQLite!");
                e.printStackTrace();
            }
            return uuids;
        });
    }

    public Connection getConnection() {
        return connection;
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