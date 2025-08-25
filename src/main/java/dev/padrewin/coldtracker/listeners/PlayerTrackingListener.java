package dev.padrewin.coldtracker.listeners;

import dev.padrewin.coldtracker.ColdTracker;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerTrackingListener implements Listener {

    private final ColdTracker plugin;

    // Real-time tracking cache - very lightweight
    private final Map<UUID, Long> realtimeJoinTimes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> cachedTotalTimes = new ConcurrentHashMap<>();

    // Cleanup task to prevent memory buildup
    private BukkitTask cleanupTask;

    public PlayerTrackingListener(ColdTracker plugin) {
        this.plugin = plugin;
        startCleanupTask();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coldtracker.tracktime")) {
            UUID playerUUID = player.getUniqueId();
            long joinTime = System.currentTimeMillis();

            // Store join time for real-time calculations
            realtimeJoinTimes.put(playerUUID, joinTime);

            // Load their total time from database and cache it
            plugin.getDatabaseManager().getTotalTimeAsync(playerUUID)
                    .thenAccept(totalTime -> cachedTotalTimes.put(playerUUID, totalTime));

            // Keep existing database logging (for batch safety system)
            plugin.getDatabaseManager().logJoinTime(playerUUID, player.getName(), joinTime);

            plugin.debugLog("Player " + player.getName() + " joined. Started real-time tracking.");
        } else {
            plugin.debugLog("Player " + player.getName() + " does not have the required permission to be tracked.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coldtracker.tracktime")) {
            UUID playerUUID = player.getUniqueId();

            // Remove from real-time cache immediately
            Long joinTime = realtimeJoinTimes.remove(playerUUID);
            if (joinTime != null) {
                long sessionTime = System.currentTimeMillis() - joinTime;
                // Update cached total with final session time
                cachedTotalTimes.merge(playerUUID, sessionTime, Long::sum);

                plugin.debugLog("Player " + player.getName() + " quit. Session time: " + sessionTime + "ms");
            }

            // Keep existing database handling
            plugin.getDatabaseManager().removeJoinTimeAsync(playerUUID);
        }
    }

    /**
     * Get real-time total playtime including current session if online
     */
    public long getRealtimeTotalTime(UUID playerUUID) {
        // Get cached database total
        long cachedTime = cachedTotalTimes.getOrDefault(playerUUID, 0L);

        // Add current session if player is online
        Long joinTime = realtimeJoinTimes.get(playerUUID);
        if (joinTime != null) {
            long currentSessionTime = System.currentTimeMillis() - joinTime;
            return cachedTime + currentSessionTime;
        }

        return cachedTime;
    }

    /**
     * Check if we have real-time data for this player
     */
    public boolean hasRealtimeData(UUID playerUUID) {
        return cachedTotalTimes.containsKey(playerUUID);
    }

    /**
     * Start periodic cleanup task to prevent memory buildup
     * Runs every 10 minutes to clean up offline players
     */
    private void startCleanupTask() {
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            cleanupOfflinePlayerData();
        }, 12000L, 12000L); // Run every 10 minutes (12000 ticks)
    }

    /**
     * Clean up cached data for players who are no longer online
     * This prevents memory buildup over time
     */
    private void cleanupOfflinePlayerData() {
        int initialSize = cachedTotalTimes.size();

        // Remove cached data for players who are offline
        Iterator<Map.Entry<UUID, Long>> iterator = cachedTotalTimes.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            UUID playerUUID = entry.getKey();

            // If player is not online and not in real-time tracking, remove from cache
            if (!realtimeJoinTimes.containsKey(playerUUID) && Bukkit.getPlayer(playerUUID) == null) {
                iterator.remove();
            }
        }

        int cleanedCount = initialSize - cachedTotalTimes.size();
        if (cleanedCount > 0) {
            plugin.debugLog("Cleaned up " + cleanedCount + " offline player entries from cache. " +
                    "Active cache size: " + cachedTotalTimes.size());
        }

        // Also verify real-time tracking consistency
        Iterator<Map.Entry<UUID, Long>> realtimeIterator = realtimeJoinTimes.entrySet().iterator();
        int realtimeCleanedCount = 0;
        while (realtimeIterator.hasNext()) {
            Map.Entry<UUID, Long> entry = realtimeIterator.next();
            UUID playerUUID = entry.getKey();

            // If player is not actually online, remove from real-time tracking
            if (Bukkit.getPlayer(playerUUID) == null) {
                realtimeIterator.remove();
                realtimeCleanedCount++;
            }
        }

        if (realtimeCleanedCount > 0) {
            plugin.debugLog("Cleaned up " + realtimeCleanedCount + " stale real-time tracking entries.");
        }
    }

    /**
     * Shutdown cleanup - cancel tasks and clear memory
     */
    public void shutdown() {
        if (cleanupTask != null && !cleanupTask.isCancelled()) {
            cleanupTask.cancel();
        }

        int totalCleared = realtimeJoinTimes.size() + cachedTotalTimes.size();
        realtimeJoinTimes.clear();
        cachedTotalTimes.clear();

        plugin.debugLog("PlayerTrackingListener shutdown. Cleared " + totalCleared + " cached entries.");
    }

    /**
     * Clear cached totals after database wipe (but keep current session times)
     */
    public void clearCacheAfterWipe() {
        int clearedCount = cachedTotalTimes.size();
        cachedTotalTimes.clear();

        // Reload fresh totals for currently online players (should be 0 after wipe)
        for (UUID playerUUID : realtimeJoinTimes.keySet()) {
            plugin.getDatabaseManager().getTotalTimeAsync(playerUUID)
                    .thenAccept(totalTime -> cachedTotalTimes.put(playerUUID, totalTime));
        }

        plugin.debugLog("Cleared " + clearedCount + " cached totals after database wipe. Reloading fresh data.");
    }
}