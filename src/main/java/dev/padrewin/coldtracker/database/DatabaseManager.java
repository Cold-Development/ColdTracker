package dev.padrewin.coldtracker.database;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.setting.SettingKey;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
            plugin.getLogger().info(ANSI_LIGHT_BLUE + "Database connected using SQLite. " + ANSI_BOLD + ANSI_GREEN + "✓" + ANSI_RESET);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize the database!");
            e.printStackTrace();
        }
    }

    private void createTables() {
        String sql = "CREATE TABLE IF NOT EXISTS staff_time ("
                + "player_uuid TEXT PRIMARY KEY,"
                + "player_name TEXT NOT NULL,"
                + "total_time INTEGER NOT NULL"
                + ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create the 'staff_time' table!");
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
        String query = "INSERT INTO staff_time (player_uuid, player_name, total_time) VALUES (?, ?, ?) "
                + "ON CONFLICT(player_uuid) DO UPDATE SET total_time = total_time + ?";
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

    public void wipeDatabaseTables() {
        String sql = "DELETE FROM staff_time";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(sql);
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("All data wiped from 'staff_time' table.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to wipe data from 'staff_time' table!");
            e.printStackTrace();
        }
    }

}
