package dev.padrewin.coldtracker.util;

import dev.padrewin.coldtracker.ColdTracker;
import dev.padrewin.coldtracker.database.MySQLStorageHandler;
import dev.padrewin.coldtracker.database.SQLiteStorageHandler;
import dev.padrewin.coldtracker.setting.SettingKey;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ServerNameResolver {

    private static String cachedServerName = null;
    private static String lastResolvedServerName = null;

    /**
     * Resolves the server name based on configuration settings
     * @return The resolved server name
     */
    public static String resolveServerName() {
        if (cachedServerName != null) {
            return cachedServerName;
        }

        String configuredName = SettingKey.SERVER_NAME.get();

        // If not set to auto, use the configured name directly
        if (!configuredName.equalsIgnoreCase("auto")) {
            cachedServerName = configuredName;
            checkForServerNameChange();
            return cachedServerName;
        }

        // Auto detection based on detection method
        String detectionMethod = SettingKey.SERVER_NAME_DETECTION.get().toLowerCase();

        try {
            switch (detectionMethod) {
                case "world":
                    cachedServerName = resolveFromWorld();
                    break;
                case "jar":
                    cachedServerName = resolveFromJar();
                    break;
                case "folder":
                    cachedServerName = resolveFromFolder();
                    break;
                case "config":
                default:
                    cachedServerName = configuredName;
                    break;
            }
        } catch (Exception e) {
            ColdTracker.getInstance().getLogger().warning("Failed to auto-detect server name: " + e.getMessage());
            cachedServerName = "Unknown";
        }

        // Fallback if we couldn't resolve anything
        if (cachedServerName == null || cachedServerName.trim().isEmpty()) {
            cachedServerName = "Unknown";
        }

        checkForServerNameChange();
        return cachedServerName;
    }

    /**
     * Checks if server name changed and offers migration
     */
    private static void checkForServerNameChange() {
        if (lastResolvedServerName != null && !lastResolvedServerName.equals(cachedServerName)) {
            ColdTracker plugin = ColdTracker.getInstance();
            plugin.getLogger().warning("===============================================");
            plugin.getLogger().warning("SERVER NAME CHANGE DETECTED!");
            plugin.getLogger().warning("Old name: " + lastResolvedServerName);
            plugin.getLogger().warning("New name: " + cachedServerName);
            plugin.getLogger().warning("===============================================");
            plugin.getLogger().warning("Your database now has mixed server names.");
            plugin.getLogger().warning("Options to fix this:");
            plugin.getLogger().warning("1. Use '/coldtracker migrate-server-name' to update old records");
            plugin.getLogger().warning("2. Use '/coldtracker export' to backup data before changes");
            plugin.getLogger().warning("3. Leave as-is (stats will show separate entries)");
            plugin.getLogger().warning("===============================================");
        }
        lastResolvedServerName = cachedServerName;
    }

    /**
     * Gets the database connection based on storage handler type
     */
    private static Connection getConnection() throws SQLException {
        ColdTracker plugin = ColdTracker.getInstance();

        if (plugin.getStorageHandler() instanceof MySQLStorageHandler) {
            MySQLStorageHandler mysqlHandler = (MySQLStorageHandler) plugin.getStorageHandler();
            return mysqlHandler.getDatabaseManager().getConnection();
        } else if (plugin.getStorageHandler() instanceof SQLiteStorageHandler) {
            SQLiteStorageHandler sqliteHandler = (SQLiteStorageHandler) plugin.getStorageHandler();
            return sqliteHandler.getDatabaseManager().getConnection();
        } else {
            throw new SQLException("Unknown storage handler type");
        }
    }

    /**
     * Migrates old server name records to new server name
     * Works with both SQLite and MySQL
     */
    public static int migrateServerName(String oldServerName, String newServerName) {
        ColdTracker plugin = ColdTracker.getInstance();
        int updatedRecords = 0;

        try {
            Connection conn = getConnection();

            // Update staff_time table
            String updateTimeQuery = "UPDATE staff_time SET server_name = ? WHERE server_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateTimeQuery)) {
                stmt.setString(1, newServerName);
                stmt.setString(2, oldServerName);
                int timeRecords = stmt.executeUpdate();
                updatedRecords += timeRecords;
                plugin.getLogger().info("Updated " + timeRecords + " records in staff_time table");
            }

            // Update staff_sessions table
            String updateSessionsQuery = "UPDATE staff_sessions SET server_name = ? WHERE server_name = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateSessionsQuery)) {
                stmt.setString(1, newServerName);
                stmt.setString(2, oldServerName);
                int sessionRecords = stmt.executeUpdate();
                updatedRecords += sessionRecords;
                plugin.getLogger().info("Updated " + sessionRecords + " records in staff_sessions table");
            }

            plugin.getLogger().info("Successfully migrated " + updatedRecords + " total records from '" +
                    oldServerName + "' to '" + newServerName + "'");

        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to migrate server names: " + e.getMessage());
            e.printStackTrace();
        }

        return updatedRecords;
    }

    /**
     * Gets all unique server names from the database
     * Works with both SQLite and MySQL
     */
    public static java.util.Set<String> getAllServerNames() {
        ColdTracker plugin = ColdTracker.getInstance();
        java.util.Set<String> serverNames = new java.util.HashSet<>();

        try {
            Connection conn = getConnection();

            // Get server names from both tables
            String[] queries = {
                    "SELECT DISTINCT server_name FROM staff_time WHERE server_name IS NOT NULL AND server_name != ''",
                    "SELECT DISTINCT server_name FROM staff_sessions WHERE server_name IS NOT NULL AND server_name != ''"
            };

            for (String query : queries) {
                try (PreparedStatement stmt = conn.prepareStatement(query);
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String serverName = rs.getString("server_name");
                        if (serverName != null && !serverName.trim().isEmpty()) {
                            serverNames.add(serverName);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to get server names: " + e.getMessage());
            e.printStackTrace();
        }

        return serverNames;
    }

    /**
     * Resolves server name from the first world's name
     */
    private static String resolveFromWorld() {
        try {
            if (!Bukkit.getWorlds().isEmpty()) {
                return capitalizeWords(Bukkit.getWorlds().get(0).getName());
            }
        } catch (Exception e) {
            ColdTracker.getInstance().getLogger().warning("Could not resolve server name from world: " + e.getMessage());
        }
        return "Unknown";
    }

    /**
     * Resolves server name from the jar file name
     */
    private static String resolveFromJar() {
        try {
            String jarPath = ColdTracker.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .getPath();
            File jarFile = new File(jarPath);
            String jarName = jarFile.getName();

            // Remove .jar extension and clean up
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4);
            }

            // Clean up common server jar prefixes/suffixes
            jarName = jarName.replaceAll("(?i)(server|spigot|paper|bukkit)", "")
                    .replaceAll("-\\d+\\.\\d+.*", "") // Remove version numbers
                    .replaceAll("[-_]+", " ") // Replace dashes/underscores with spaces
                    .trim();

            if (!jarName.isEmpty()) {
                return capitalizeWords(jarName);
            }
        } catch (Exception e) {
            ColdTracker.getInstance().getLogger().warning("Could not resolve server name from jar: " + e.getMessage());
        }
        return "Unknown";
    }

    /**
     * Resolves server name from the server folder name
     */
    private static String resolveFromFolder() {
        try {
            File currentDir = new File("").getAbsoluteFile();
            String folderName = currentDir.getName();

            // Clean up the folder name
            folderName = folderName.replaceAll("[-_]+", " ").trim();

            if (!folderName.isEmpty()) {
                return capitalizeWords(folderName);
            }
        } catch (Exception e) {
            ColdTracker.getInstance().getLogger().warning("Could not resolve server name from folder: " + e.getMessage());
        }
        return "Unknown";
    }

    /**
     * Capitalizes the first letter of each word
     */
    private static String capitalizeWords(String input) {
        if (input == null || input.trim().isEmpty()) {
            return input;
        }

        String[] words = input.trim().split("\\s+");
        StringBuilder result = new StringBuilder();

        for (String word : words) {
            if (word.length() > 0) {
                if (result.length() > 0) {
                    result.append(" ");
                }
                result.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) {
                    result.append(word.substring(1).toLowerCase());
                }
            }
        }

        return result.toString();
    }

    /**
     * Clears the cached server name (useful for config reloads)
     */
    public static void clearCache() {
        cachedServerName = null;
        // Don't clear lastResolvedServerName so we can detect changes
    }

    /**
     * Gets the current cached server name without resolving
     */
    public static String getCachedServerName() {
        return cachedServerName;
    }
}