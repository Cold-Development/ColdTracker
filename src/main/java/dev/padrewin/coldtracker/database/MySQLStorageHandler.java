package dev.padrewin.coldtracker.database;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.util.ServerNameResolver;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MySQLStorageHandler implements StorageHandler {

    private final MySQLDatabaseManager databaseManager;
    private final ColdTracker plugin;
    private BukkitTask batchUpdaterTask; // Track the task

    public MySQLStorageHandler(ColdTracker plugin) {
        this.plugin = plugin;
        this.databaseManager = new MySQLDatabaseManager(plugin);
    }

    private String resolveServerName() {
        return ServerNameResolver.resolveServerName();
    }

    /**
     * Provides access to the database manager for migration operations
     * @return The MySQL database manager instance
     */
    public MySQLDatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @Override
    public CompletableFuture<Long> getTotalTimeAsync(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT total_time FROM staff_time WHERE player_uuid = ?";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getLong("total_time");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to get total time for player " + playerUUID);
                e.printStackTrace();
            }
            return 0L;
        });
    }

    @Override
    public CompletableFuture<Integer> getTotalVotesAsync(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT COUNT(*) AS vote_count FROM staff_votes WHERE player_uuid = ?";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt("vote_count");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to get total votes for player " + playerUUID);
                e.printStackTrace();
            }
            return 0;
        });
    }

    @Override
    public CompletableFuture<List<VoteRecord>> getAllVotes() {
        return CompletableFuture.supplyAsync(() -> {
            List<VoteRecord> votes = new ArrayList<>();
            String query = "SELECT player_uuid, player_name, service_name, vote_time FROM staff_votes";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    String playerName = rs.getString("player_name");
                    String serviceName = rs.getString("service_name");
                    String voteTime = rs.getString("vote_time");
                    votes.add(new VoteRecord(uuid, playerName, serviceName, voteTime));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to fetch votes from database");
                e.printStackTrace();
            }
            return votes;
        });
    }

    @Override
    public CompletableFuture<Map<String, Long>> getPlaytimeByServer(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Long> map = new HashMap<>();
            String query = "SELECT server_name, total_time FROM staff_time WHERE player_uuid = ?";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String server = rs.getString("server_name");
                    long time = rs.getLong("total_time");
                    if (server == null || server.isBlank()) server = "unknown";
                    map.put(server, time);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to get playtime by server for " + playerUUID);
                e.printStackTrace();
            }
            return map;
        });
    }

    @Override
    public void logJoinTime(UUID playerUUID, String playerName, long joinTime) {
        CompletableFuture.runAsync(() -> {
            String query = "INSERT INTO staff_sessions (player_uuid, player_name, join_time, server_name) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE join_time = VALUES(join_time), server_name = VALUES(server_name)";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, joinTime);
                stmt.setString(4, resolveServerName());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to log join time for " + playerName);
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<Void> removeJoinTimeAsync(UUID playerUUID) {
        return CompletableFuture.runAsync(() -> {
            String select = "SELECT join_time FROM staff_sessions WHERE player_uuid = ?";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(select)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    long joinTime = rs.getLong("join_time");
                    long sessionTime = System.currentTimeMillis() - joinTime;
                    addPlaySession(playerUUID, sessionTime);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to remove join time for " + playerUUID);
                e.printStackTrace();
            }

            String delete = "DELETE FROM staff_sessions WHERE player_uuid = ?";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(delete)) {
                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to delete session for " + playerUUID);
                e.printStackTrace();
            }
        });
    }

    @Override
    public void addPlaySession(UUID playerUUID, long sessionTime) {
        CompletableFuture.runAsync(() -> {
            String playerName = Bukkit.getOfflinePlayer(playerUUID).getName();
            if (playerName == null) return;

            String query = "INSERT INTO staff_time (player_uuid, player_name, total_time, server_name) VALUES (?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE total_time = total_time + VALUES(total_time), server_name = VALUES(server_name)";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.setLong(3, sessionTime);
                stmt.setString(4, resolveServerName());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to update playtime for " + playerUUID);
                e.printStackTrace();
            }
        });
    }

    @Override
    public void addVote(UUID playerUUID, String playerName, String serviceName, String timestamp) {
        CompletableFuture.runAsync(() -> {
            try {
                String formattedTimestamp = timestamp;

                if (timestamp.matches("\\d+")) {
                    long epoch = Long.parseLong(timestamp);
                    if (timestamp.length() == 13) {
                        epoch /= 1000;
                    }
                    LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault());
                    formattedTimestamp = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                }

                String query = "INSERT INTO staff_votes (player_uuid, player_name, service_name, vote_time) VALUES (?, ?, ?, ?)";
                try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query)) {
                    stmt.setString(1, playerUUID.toString());
                    stmt.setString(2, playerName);
                    stmt.setString(3, serviceName);
                    stmt.setString(4, formattedTimestamp);
                    stmt.executeUpdate();
                }
            } catch (Exception e) {
                plugin.getLogger().severe("[MySQL] Failed to log vote for " + playerName + ": " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    @Override
    public void wipeDatabaseTables() {
        try (Statement stmt = databaseManager.getConnection().createStatement()) {
            stmt.executeUpdate("DELETE FROM staff_time");
            stmt.executeUpdate("DELETE FROM staff_votes");
        } catch (SQLException e) {
            plugin.getLogger().severe("[MySQL] Failed to wipe database tables");
            e.printStackTrace();
        }
    }

    @Override
    public void cleanupStaleSessions() {
        CompletableFuture.runAsync(() -> {
            String query = "SELECT player_uuid, join_time FROM staff_sessions";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    if (Bukkit.getPlayer(playerUUID) == null) {
                        long joinTime = rs.getLong("join_time");
                        long sessionTime = System.currentTimeMillis() - joinTime;
                        addPlaySession(playerUUID, sessionTime);
                        removeJoinTimeAsync(playerUUID);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to cleanup stale sessions");
                e.printStackTrace();
            }
        });
    }

    @Override
    public CompletableFuture<Set<UUID>> getAllPlayerUUIDs() {
        return CompletableFuture.supplyAsync(() -> {
            Set<UUID> uuids = new HashSet<>();
            String query = "SELECT DISTINCT player_uuid FROM staff_time";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String uuidStr = rs.getString("player_uuid");
                    if (uuidStr != null && !uuidStr.isEmpty()) {
                        uuids.add(UUID.fromString(uuidStr));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to get all player UUIDs");
                e.printStackTrace();
            }
            return uuids;
        });
    }

    @Override
    public void startBatchUpdater() {
        // Stop any existing batch updater first
        stopBatchUpdater();

        batchUpdaterTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            boolean debug = plugin.getConfig().getBoolean("debug", false);
            if (debug) {
                plugin.getLogger().info("Running batch updater (MySQL)...");
            }

            String query = "SELECT player_uuid, join_time FROM staff_sessions";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    if (Bukkit.getPlayer(playerUUID) == null) {
                        long joinTime = rs.getLong("join_time");
                        long sessionTime = System.currentTimeMillis() - joinTime;
                        addPlaySession(playerUUID, sessionTime);
                        removeJoinTimeAsync(playerUUID);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("[MySQL] Failed to execute batch playtime update!");
                e.printStackTrace();
            }

            if (debug) {
                plugin.getLogger().info("Batch playtime update (MySQL) completed.");
            }
        }, 20 * 60, 20 * 60);
    }

    @Override
    public void stopBatchUpdater() {
        if (batchUpdaterTask != null && !batchUpdaterTask.isCancelled()) {
            batchUpdaterTask.cancel();
            boolean debug = plugin.getConfig().getBoolean("debug", false);
            if (debug) {
                plugin.getLogger().info("[MySQL] Batch updater stopped.");
            }
        }
    }

    @Override
    public void closeConnection() {
        stopBatchUpdater(); // Stop batch updater before closing
        databaseManager.closeConnection();
    }
}