package dev.padrewin.coldtracker;

import dev.padrewin.colddev.ColdPlugin;
import dev.padrewin.colddev.config.ColdSetting;
import dev.padrewin.colddev.database.DatabaseConnector;
import dev.padrewin.colddev.database.SQLiteConnector;
import dev.padrewin.colddev.manager.Manager;
import dev.padrewin.colddev.manager.PluginUpdateManager;
import dev.padrewin.coldtracker.database.DatabaseManager;
import dev.padrewin.coldtracker.listeners.PlayerTrackingListener;
import dev.padrewin.coldtracker.listeners.StaffVoteListener;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import dev.padrewin.coldtracker.setting.SettingKey;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import static dev.padrewin.colddev.manager.AbstractDataManager.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class ColdTracker extends ColdPlugin {

    /**
     * Console colors
     */
    String ANSI_RESET = "\u001B[0m";
    String ANSI_CHINESE_PURPLE = "\u001B[38;5;93m";
    String ANSI_PURPLE = "\u001B[35m";
    String ANSI_GREEN = "\u001B[32m";
    String ANSI_RED = "\u001B[31m";
    String ANSI_AQUA = "\u001B[36m";
    String ANSI_PINK = "\u001B[35m";
    String ANSI_YELLOW = "\u001B[33m";

    private static ColdTracker instance;
    private LuckPerms luckPerms;
    private boolean votifierAvailable;
    private final Map<UUID, Long> joinTimes = new HashMap<>();

    public ColdTracker() {
        super("Cold-Development", "ColdTracker", 23682, null, LocaleManager.class, null);
        instance = this;
    }
    private DatabaseManager databaseManager;

    @Override
    public void enable() {
        instance = this;

        setupLuckPerms();
        setupVotifier();

        // Initialize DatabaseManager
        databaseManager = new DatabaseManager(this, "coldtracker.db");
        DatabaseConnector connector;
        connector = new SQLiteConnector(this);
        String databasePath = connector.getDatabasePath();
        getLogger().info(ANSI_GREEN + "Database path: " + ANSI_YELLOW + databasePath + ANSI_RESET);

        // Cleanup last join sessions
        databaseManager.cleanupStaleSessions();

        // Initialize time tracking event listener
        getServer().getPluginManager().registerEvents(new PlayerTrackingListener(this), this);

        // Initialize vote tracking event listener
        if (votifierAvailable) {
            getServer().getPluginManager().registerEvents(new StaffVoteListener(this), this);
        }

        getManager(PluginUpdateManager.class);

        String name = getDescription().getName();
        getLogger().info("");
        getLogger().info(ANSI_CHINESE_PURPLE + "  ____ ___  _     ____  " + ANSI_RESET);
        getLogger().info(ANSI_PINK + " / ___/ _ \\| |   |  _ \\ " + ANSI_RESET);
        getLogger().info(ANSI_CHINESE_PURPLE + "| |  | | | | |   | | | |" + ANSI_RESET);
        getLogger().info(ANSI_PINK + "| |__| |_| | |___| |_| |" + ANSI_RESET);
        getLogger().info(ANSI_CHINESE_PURPLE + " \\____\\___/|_____|____/ " + ANSI_RESET);
        getLogger().info("    " + ANSI_GREEN + name + ANSI_RED + " v" + getDescription().getVersion() + ANSI_RESET);
        getLogger().info(ANSI_PURPLE + "    Author(s): " + ANSI_PURPLE + getDescription().getAuthors().get(0) + ANSI_RESET);
        getLogger().info(ANSI_AQUA + "    (c) Cold Development ❄" + ANSI_RESET);
        getLogger().info("");


        File configFile = new File(getDataFolder(), "en_US.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }

        saveDefaultConfig();
    }

    @Override
    public void disable() {
        getLogger().info("[DEBUG] Processing remaining playtime before shutdown...");

        if (databaseManager != null) {
            List<CompletableFuture<Void>> tasks = new ArrayList<>();

            if (!Bukkit.getOnlinePlayers().isEmpty()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission("coldtracker.tracktime")) {
                        CompletableFuture<Void> task = databaseManager.removeJoinTimeAsync(player.getUniqueId())
                                .exceptionally(ex -> {
                                    getLogger().severe("[ERROR] Failed to remove join time for " + player.getName() + ": " + ex.getMessage());
                                    return null;
                                });
                        tasks.add(task);
                    }
                }
            }

            if (!tasks.isEmpty()) {
                CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).thenRun(() -> {
                    databaseManager.closeConnection();
                    getLogger().info("");
                    getLogger().info("[DEBUG] Database connection closed successfully.");
                    getLogger().info(ANSI_CHINESE_PURPLE + "ColdTracker disabled." + ANSI_RESET);
                    getLogger().info("");
                });
            } else {
                databaseManager.closeConnection();
                getLogger().info("");
                getLogger().info("[DEBUG] No active players to process, database closed immediately.");
                getLogger().info(ANSI_CHINESE_PURPLE + "ColdTracker disabled." + ANSI_RESET);
                getLogger().info("");
            }
        } else {
            getLogger().info("");
            getLogger().info(ANSI_CHINESE_PURPLE + "ColdTracker disabled." + ANSI_RESET);
            getLogger().info("");
        }
    }


    @Override
    public void reload() {
        super.reload();
    }

    public Map<UUID, Long> getJoinTimes() {
        return joinTimes;
    }

    @Override
    protected List<Class<? extends Manager>> getManagerLoadPriority() {
        return List.of(
                CommandManager.class
        );
    }

    @Override
    protected List<ColdSetting<?>> getColdConfigSettings() {
        return SettingKey.getKeys();
    }

    @Override
    protected String[] getColdConfigHeader() {
        return new String[] {
                " ██████╗ ██████╗ ██╗     ██████╗ ",
                "██╔════╝██╔═══██╗██║     ██╔══██╗",
                "██║     ██║   ██║██║     ██║  ██║",
                "██║     ██║   ██║██║     ██║  ██║",
                "╚██████╗╚██████╔╝███████╗██████╔╝",
                " ╚═════╝ ╚═════╝ ╚══════╝╚═════╝ ",
                "                                 "
        };
    }

    public static ColdTracker getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ColdTracker instance is not initialized!");
        }
        return instance;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public LuckPerms getLuckPerms() {
        return luckPerms;
    }

    private void setupLuckPerms() {
        RegisteredServiceProvider<LuckPerms> provider = getServer().getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            luckPerms = provider.getProvider();
            getLogger().info(ANSI_LIGHT_BLUE + "LuckPerms API loaded successfully. " + ANSI_BOLD + ANSI_GREEN + "✔" + ANSI_RESET);

        } else {
            getLogger().warning(ANSI_LIGHT_BLUE + "LuckPerms API not found. " + ANSI_BOLD + ANSI_RED + "✘" + ANSI_RESET);
        }
    }

    public boolean isVotifierAvailable() {
        return votifierAvailable;
    }

    private void setupVotifier() {
        if (getServer().getPluginManager().isPluginEnabled("Votifier") ||
                getServer().getPluginManager().isPluginEnabled("nuvotifier")) {
            votifierAvailable = true;
            getLogger().info(ANSI_LIGHT_BLUE + "Votifier API loaded successfully. " + ANSI_BOLD + ANSI_GREEN + "✔" + ANSI_RESET);
        } else {
            votifierAvailable = false;
            getLogger().warning(ANSI_LIGHT_BLUE + "No voting plugin found (nuvotifier). Vote-related features will be disabled. " + ANSI_BOLD + ANSI_RED + "✘" + ANSI_RESET);
        }
    }

}
