package dev.padrewin.coldtracker.integration;

import dev.padrewin.coldtracker.ColdTracker;
import litebans.api.Database;
import org.bukkit.Bukkit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridges ColdTracker to LiteBans' own connection pool via its public API,
 * so sanction counts stay accurate whether LiteBans runs on SQLite or MySQL.
 */
public class LiteBansHook {

    private final ColdTracker plugin;
    private final boolean available;
    private final ExecutorService executor;

    public LiteBansHook(ColdTracker plugin) {
        this.plugin = plugin;
        this.available = Bukkit.getPluginManager().isPluginEnabled("LiteBans");

        if (this.available) {
            this.executor = Executors.newSingleThreadExecutor(new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "ColdTracker-LiteBans-" + counter.getAndIncrement());
                    thread.setDaemon(true);
                    return thread;
                }
            });
        } else {
            this.executor = null;
        }
    }

    public boolean isAvailable() {
        return this.available;
    }

    /**
     * Fetches sanction counts (mute/ban/kick/warning) issued by a staff member since the given
     * period start (epoch millis). Pass 0 for lifetime totals. LiteBans queries must not run on
     * the main thread, so this always resolves off it.
     */
    public CompletableFuture<SanctionCounts> getSanctionCountsAsync(UUID staffUUID, long sincePeriodStart) {
        if (!this.available) {
            return CompletableFuture.completedFuture(SanctionCounts.EMPTY);
        }

        CompletableFuture<SanctionCounts> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    logRawSample("{mutes}");
                }
                int mutes = countEntries("{mutes}", staffUUID, sincePeriodStart);
                int bans = countEntries("{bans}", staffUUID, sincePeriodStart);
                int kicks = countEntries("{kicks}", staffUUID, sincePeriodStart);
                int warnings = countEntries("{warnings}", staffUUID, sincePeriodStart);
                future.complete(new SanctionCounts(mutes, bans, kicks, warnings));
            } catch (Throwable t) {
                plugin.getLogger().severe("Failed to fetch LiteBans sanction counts for " + staffUUID + ": " + t.getMessage());
                future.complete(SanctionCounts.EMPTY);
            }
        });
        return future;
    }

    private int countEntries(String table, UUID staffUUID, long sincePeriodStart) throws SQLException {
        String query = "SELECT COUNT(*) FROM " + table + " WHERE banned_by_uuid = ?"
                + (sincePeriodStart > 0 ? " AND time >= ?" : "");
        int result = 0;
        try (PreparedStatement stmt = Database.get().prepareStatement(query)) {
            stmt.setString(1, staffUUID.toString());
            if (sincePeriodStart > 0) {
                stmt.setLong(2, sincePeriodStart);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    result = rs.getInt(1);
                }
            }
        }
        plugin.debugLog("[LiteBans] " + table + " -> uuid=" + staffUUID + " sincePeriodStart=" + sincePeriodStart + " result=" + result);
        return result;
    }

    /**
     * Debug-only helper: prints the raw banned_by_uuid/time of the most recent rows so the stored
     * UUID format (dashed vs not) and timestamp unit can be sanity-checked against what we query with.
     */
    private void logRawSample(String table) {
        String query = "SELECT banned_by_uuid, banned_by_name, time FROM " + table + " ORDER BY id DESC LIMIT 3";
        try (PreparedStatement stmt = Database.get().prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                plugin.debugLog("[LiteBans] " + table + " sample -> banned_by_uuid=" + rs.getString("banned_by_uuid")
                        + " banned_by_name=" + rs.getString("banned_by_name")
                        + " time=" + rs.getLong("time"));
            }
        } catch (SQLException e) {
            plugin.debugWarn("[LiteBans] Failed to fetch raw sample from " + table + ": " + e.getMessage());
        }
    }

    public void shutdown() {
        if (this.executor != null) {
            this.executor.shutdown();
        }
    }
}
