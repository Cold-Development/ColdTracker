package dev.padrewin.coldtracker.database;

import dev.padrewin.coldtracker.ColdTracker;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.UUID;

import static dev.padrewin.colddev.manager.AbstractDataManager.*;

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
            plugin.getLogger().info(ANSI_LIGHT_BLUE + "Database connected using SQLite. " + ANSI_BOLD + ANSI_GREEN + "✔" + ANSI_RESET);
        } catch (SQLException e) {
            plugin.getLogger().warning(ANSI_RED + "Database failed to connect. " + ANSI_BOLD + ANSI_RED + "✘" + ANSI_RESET);
            e.printStackTrace();
        }
    }

    private void createTables() {
        // Table for playtime
        String createPlaytimeTable = "CREATE TABLE IF NOT EXISTS staff_time (" +
                "player_uuid TEXT PRIMARY KEY," +
                "player_name TEXT NOT NULL," +
                "total_time INTEGER NOT NULL" +
                ");";

        // Table for votes
        String createVotesTable = "CREATE TABLE IF NOT EXISTS staff_votes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "player_uuid TEXT NOT NULL," +
                "player_name TEXT NOT NULL," +
                "service_name TEXT NOT NULL," +
                "vote_time TEXT NOT NULL" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createPlaytimeTable);
            stmt.execute(createVotesTable);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create database tables!");
            e.printStackTrace();
        }
    }

    public long getTotalTime(UUID playerUUID) {
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
        return 0;
    }

    public void addPlaySession(UUID playerUUID, String playerName, long sessionTime) {
        String query = "INSERT INTO staff_time (player_uuid, player_name, total_time) VALUES (?, ?, ?) " +
                "ON CONFLICT(player_uuid) DO UPDATE SET total_time = total_time + ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[DEBUG] Adding play session for player " + playerName + " with sessionTime: " + sessionTime);
            }

            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, playerName);
            stmt.setLong(3, sessionTime);
            stmt.setLong(4, sessionTime);
            stmt.executeUpdate();

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[DEBUG] Successfully added play session for player " + playerName);
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to add play session for player " + playerUUID + "!");
            e.printStackTrace();
        }
    }

    public void addVote(UUID playerUUID, String playerName, String serviceName, String timestamp) {
        String query = "INSERT INTO staff_votes (player_uuid, player_name, service_name, vote_time) " +
                "VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, serviceName);
            stmt.setString(4, timestamp);
            stmt.executeUpdate();

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Vote logged for player " + playerName +
                        " (Service: " + serviceName + ", Timestamp: " + timestamp + ").");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to log vote for player " + playerName + "!");
            e.printStackTrace();
        }
    }

    public int getTotalVotes(UUID playerUUID) {
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
    }

    public void wipeDatabaseTables() {
        String sql1 = "DELETE FROM staff_time";
        String sql2 = "DELETE FROM staff_votes";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql1);
            stmt.executeUpdate(sql2);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("All data wiped from 'staff_time' and 'staff_votes' tables.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to wipe data from database tables!");
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        // Procesăm timpul și voturile pentru toți jucătorii conectați înainte de a închide baza de date
        Bukkit.getOnlinePlayers().forEach(player -> {
            UUID playerUUID = player.getUniqueId();
            long leaveTime = System.currentTimeMillis();

            // Procesăm timpul de joc pentru jucătorii cu permisiunea "coldtracker.tracktime"
            if (player.hasPermission("coldtracker.tracktime")) {
                Long joinTime = plugin.getJoinTimes().remove(playerUUID);

                if (joinTime != null) {
                    long sessionTime = leaveTime - joinTime;
                    addPlaySession(playerUUID, player.getName(), sessionTime);

                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("[DEBUG] Processed disconnect for player " + player.getName() +
                                ". Session time: " + sessionTime + " ms");
                    }
                } else if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("[DEBUG] No join time found for player " + player.getName());
                }
            }

            // Procesăm voturile pentru jucătorii cu permisiunea "coldtracker.trackvote"
            if (player.hasPermission("coldtracker.trackvote") && plugin.getConfig().getBoolean("track-votes", false)) {
                int totalVotes = getTotalVotes(playerUUID);

                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("[DEBUG] Processed votes for player " + player.getName() +
                            ". Total votes: " + totalVotes);
                }

                // Aici putem adăuga alte operațiuni dacă e necesar
            }
        });

        // Închidem conexiunea la baza de date
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
