package dev.padrewin.coldtracker;

import dev.padrewin.colddev.ColdPlugin;
import dev.padrewin.colddev.config.ColdSetting;
import dev.padrewin.colddev.manager.Manager;
import dev.padrewin.colddev.manager.PluginUpdateManager;
import dev.padrewin.coldtracker.database.MySQLStorageHandler;
import dev.padrewin.coldtracker.database.SQLiteStorageHandler;
import dev.padrewin.coldtracker.database.StorageHandler;
import dev.padrewin.coldtracker.listeners.PlayerTrackingListener;
import dev.padrewin.coldtracker.listeners.StaffVoteListener;
import dev.padrewin.coldtracker.manager.CommandManager;
import dev.padrewin.coldtracker.manager.LocaleManager;
import dev.padrewin.coldtracker.rotation.RotationScheduler;
import dev.padrewin.coldtracker.setting.SettingKey;
import dev.padrewin.coldtracker.util.ServerNameResolver;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static dev.padrewin.colddev.manager.AbstractDataManager.ANSI_BOLD;
import static dev.padrewin.colddev.manager.AbstractDataManager.ANSI_LIGHT_BLUE;

public final class ColdTracker extends ColdPlugin {

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
    private StorageHandler storageHandler;

    public ColdTracker() {
        super("Cold-Development", "ColdTracker", 23682, null, LocaleManager.class, null);
        instance = this;
    }

    @Override
    public void enable() {
        instance = this;

        setupLuckPerms();
        setupVotifier();

        setupStorageHandler();

        if (storageHandler != null) {
            storageHandler.cleanupStaleSessions();
            storageHandler.startBatchUpdater();
        } else {
            getLogger().severe("StorageHandler is null! Check setupStorageHandler()");
        }

        new RotationScheduler(this).start();

        getServer().getPluginManager().registerEvents(new PlayerTrackingListener(this), this);

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
        boolean debug = getConfig().getBoolean("debug", false);

        if (debug) {
            getLogger().info("[DEBUG] Processing remaining playtime before shutdown...");
        }

        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission("coldtracker.tracktime")) {
                CompletableFuture<Void> task = storageHandler.removeJoinTimeAsync(player.getUniqueId())
                        .exceptionally(ex -> {
                            getLogger().severe("[ERROR] Failed to remove join time for " + player.getName() + ": " + ex.getMessage());
                            return null;
                        });
                tasks.add(task);
            }
        }

        // Wait for all tasks to complete with a timeout
        CompletableFuture<Void> allTasks = CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0]));

        try {
            allTasks.get(10, TimeUnit.SECONDS); // Wait max 10 seconds
        } catch (Exception e) {
            if (debug) {
                getLogger().warning("[DEBUG] Timeout or error waiting for playtime processing: " + e.getMessage());
            }
        }

        // Always close the storage handler
        if (storageHandler != null) {
            storageHandler.closeConnection();
            if (debug) {
                getLogger().info("[DEBUG] Database connection closed successfully.");
            }
        }

        getLogger().info("");
        getLogger().info(ANSI_CHINESE_PURPLE + "ColdTracker disabled." + ANSI_RESET);
        getLogger().info("");
    }


    public void setupStorageHandler() {
        boolean debug = getConfig().getBoolean("debug", false);

        // Properly close existing storage handler if it exists
        if (this.storageHandler != null) {
            if (debug) {
                getLogger().info("Switching storage handlers - closing current connection...");
            }
            this.storageHandler.closeConnection(); // This now stops batch updater too
            this.storageHandler = null;
        }

        String type = SettingKey.STORAGE_TYPE.get().toLowerCase();
        if (type.equals("mysql")) {
            this.storageHandler = new MySQLStorageHandler(this);
            if (debug) {
                getLogger().info("Using MySQL as data storage.");
            }
        } else {
            this.storageHandler = new SQLiteStorageHandler(this);
            if (debug) {
                getLogger().info("Using SQLite as data storage.");
            }
        }

        // Start the batch updater for the new storage handler
        if (this.storageHandler != null) {
            this.storageHandler.startBatchUpdater();
            if (debug) {
                getLogger().info("Storage handler initialized and batch updater started.");
            }
        }
    }

    public StorageHandler getStorageHandler() {
        return storageHandler;
    }

    @Override
    public void reload() {
        super.reload();

        // Clear cached server name so it gets re-resolved with new config
        ServerNameResolver.clearCache();

        // Check if storage type changed and reinitialize if needed
        String currentType = storageHandler instanceof MySQLStorageHandler ? "mysql" : "sqlite";
        String configType = SettingKey.STORAGE_TYPE.get().toLowerCase();

        if (!currentType.equals(configType)) {
            getLogger().info("[ColdTracker] Storage type changed from " + currentType + " to " + configType + " - reinitializing...");
            setupStorageHandler();
        }
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