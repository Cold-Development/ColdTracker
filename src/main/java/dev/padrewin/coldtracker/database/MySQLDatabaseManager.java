package dev.padrewin.coldtracker.database;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.setting.SettingKey;
import org.bukkit.Bukkit;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public class MySQLDatabaseManager {

    private final ColdTracker plugin;
    private Connection connection;

    public MySQLDatabaseManager(ColdTracker plugin) {
        this.plugin = plugin;
        connect();
        createTables();
    }

    private void connect() {
        try {
            String host = SettingKey.MYSQL_HOST.get();
            int port = SettingKey.MYSQL_PORT.get();
            String database = SettingKey.MYSQL_DATABASE.get();
            String username = SettingKey.MYSQL_USERNAME.get();
            String password = SettingKey.MYSQL_PASSWORD.get();

            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);

            plugin.getLogger().info("MySQL database connected successfully.");
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to MySQL database!");
            e.printStackTrace();
        }
    }

    private void createTables() {
        String createPlaytimeTable = "CREATE TABLE IF NOT EXISTS staff_time (" +
                "player_uuid VARCHAR(36) PRIMARY KEY," +
                "player_name VARCHAR(32) NOT NULL," +
                "total_time BIGINT NOT NULL DEFAULT 0," +
                "server_name VARCHAR(32) DEFAULT NULL" +
                ");";

        String createSessionsTable = "CREATE TABLE IF NOT EXISTS staff_sessions (" +
                "player_uuid VARCHAR(36) PRIMARY KEY," +
                "player_name VARCHAR(32) NOT NULL," +
                "join_time BIGINT NOT NULL," +
                "server_name VARCHAR(32) DEFAULT NULL" +
                ");";

        String createVotesTable = "CREATE TABLE IF NOT EXISTS staff_votes (" +
                "id INT AUTO_INCREMENT PRIMARY KEY," +
                "player_uuid VARCHAR(36) NOT NULL," +
                "player_name VARCHAR(32) NOT NULL," +
                "service_name VARCHAR(64) NOT NULL," +
                "vote_time DATETIME NOT NULL" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate(createPlaytimeTable);
            stmt.executeUpdate(createSessionsTable);
            stmt.executeUpdate(createVotesTable);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to create MySQL tables!");
            e.printStackTrace();
        }
    }

    /**
     * Provides access to the database connection
     * @return The database connection
     */
    public Connection getConnection() {
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("MySQL connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close MySQL connection.");
            e.printStackTrace();
        }
    }
}
