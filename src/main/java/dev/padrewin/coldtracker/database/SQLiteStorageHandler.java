package dev.padrewin.coldtracker.database;

import dev.padrewin.coldtracker.ColdTracker;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class SQLiteStorageHandler implements StorageHandler {

    private final DatabaseManager databaseManager;
    private final ColdTracker plugin;
    private BukkitTask batchUpdaterTask; // Track the task

    public SQLiteStorageHandler(ColdTracker plugin) {
        this.plugin = plugin;
        this.databaseManager = new DatabaseManager(plugin, "sqlite");
    }

    @Override
    public CompletableFuture<Long> getTotalTimeAsync(UUID playerUUID) {
        return databaseManager.getTotalTimeAsync(playerUUID);
    }

    @Override
    public CompletableFuture<Integer> getTotalVotesAsync(UUID playerUUID) {
        return databaseManager.getTotalVotesAsync(playerUUID);
    }

    @Override
    public CompletableFuture<Map<String, Long>> getPlaytimeByServer(UUID playerUUID) {
        return databaseManager.getPlaytimeByServer(playerUUID);
    }

    @Override
    public void logJoinTime(UUID playerUUID, String playerName, long joinTime) {
        databaseManager.logJoinTime(playerUUID, playerName, joinTime);
    }

    @Override
    public CompletableFuture<Void> removeJoinTimeAsync(UUID playerUUID) {
        return databaseManager.removeJoinTimeAsync(playerUUID);
    }

    @Override
    public void addPlaySession(UUID playerUUID, long sessionTime) {
        databaseManager.addPlaySession(playerUUID, sessionTime);
    }

    @Override
    public void addVote(UUID playerUUID, String playerName, String serviceName, String timestamp) {
        databaseManager.addVote(playerUUID, playerName, serviceName, timestamp);
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
                plugin.getLogger().severe("[SQLite] Failed to get all player UUIDs");
                e.printStackTrace();
            }
            return uuids;
        });
    }

    public CompletableFuture<List<VoteRecord>> getVotes(UUID playerUUID) {
        return CompletableFuture.supplyAsync(() -> {
            List<VoteRecord> votes = new ArrayList<>();
            String query = "SELECT player_name, service_name, vote_time FROM staff_votes WHERE player_uuid = ?";
            try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query)) {
                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String playerName = rs.getString("player_name");
                    String serviceName = rs.getString("service_name");
                    String voteTime = rs.getString("vote_time");
                    votes.add(new VoteRecord(playerUUID, playerName, serviceName, voteTime));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get votes for player " + playerUUID);
                e.printStackTrace();
            }
            return votes;
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
                    UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                    String playerName = rs.getString("player_name");
                    String serviceName = rs.getString("service_name");
                    String voteTime = rs.getString("vote_time");

                    votes.add(new VoteRecord(playerUUID, playerName, serviceName, voteTime));
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to get all votes from SQLite!");
                e.printStackTrace();
            }
            return votes;
        });
    }

    @Override
    public void startBatchUpdater() {
        // Stop any existing batch updater first
        stopBatchUpdater();

        batchUpdaterTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            try {
                boolean debug = plugin.getConfig().getBoolean("debug", false);
                if (debug) {
                    plugin.getLogger().info("Running batch updater (SQLite)...");
                }

                String query = "SELECT player_uuid, join_time FROM staff_sessions";
                try (PreparedStatement stmt = databaseManager.getConnection().prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {

                    while (rs.next()) {
                        UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                        long joinTime = rs.getLong("join_time");

                        if (Bukkit.getPlayer(playerUUID) == null) {
                            long sessionTime = System.currentTimeMillis() - joinTime;
                            addPlaySession(playerUUID, sessionTime);
                            removeJoinTimeAsync(playerUUID).exceptionally(ex -> {
                                plugin.getLogger().severe("[ERROR] Failed to remove stale session for " + playerUUID + ": " + ex.getMessage());
                                return null;
                            });

                            if (debug) {
                                plugin.getLogger().info("[DEBUG] Removed stale session for " + playerUUID);
                            }
                        }
                    }
                }

                if (debug) {
                    plugin.getLogger().info("Batch playtime update (SQLite) completed.");
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to execute batch playtime update (SQLite)!");
                e.printStackTrace();
            }
        }, 20 * 60, 20 * 60);
    }

    @Override
    public void stopBatchUpdater() {
        if (batchUpdaterTask != null && !batchUpdaterTask.isCancelled()) {
            batchUpdaterTask.cancel();
            boolean debug = plugin.getConfig().getBoolean("debug", false);
            if (debug) {
                plugin.getLogger().info("[SQLite] Batch updater stopped.");
            }
        }
    }

    /**
     * Provides access to the database manager for migration operations
     * @return The database manager instance
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @Override
    public void wipeDatabaseTables() {
        databaseManager.wipeDatabaseTables();
    }

    @Override
    public void cleanupStaleSessions() {
        databaseManager.cleanupStaleSessions();
    }

    @Override
    public void closeConnection() {
        stopBatchUpdater(); // Stop batch updater before closing
        databaseManager.closeConnection();
    }
}