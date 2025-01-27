package dev.padrewin.coldtracker.listeners;

import dev.padrewin.coldtracker.ColdTracker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerTrackingListener implements Listener {

    private final ColdTracker plugin;

    public PlayerTrackingListener(ColdTracker plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coldtracker.tracktime")) {
            UUID playerUUID = player.getUniqueId();
            long joinTime = System.currentTimeMillis();

            // Store join time in the global map
            plugin.getJoinTimes().put(playerUUID, joinTime);
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[DEBUG] Player " + player.getName() + " joined. Join time: " + new java.text.SimpleDateFormat("dd-MM-yyyy | HH:mm:ss").format(new java.util.Date(joinTime)));
            }
        } else {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[DEBUG] Player " + player.getName() + " does not have the required permission to be tracked.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coldtracker.tracktime")) {
            UUID playerUUID = player.getUniqueId();
            long leaveTime = System.currentTimeMillis();

            Long joinTime = plugin.getJoinTimes().remove(playerUUID);
            if (joinTime != null) {
                long sessionTime = leaveTime - joinTime;
                String playerName = player.getName();
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().warning("[DEBUG] Player " + playerName + " quit. Join time: " + new java.text.SimpleDateFormat("dd-MM-yyyy | HH:mm:ss").format(new java.util.Date(joinTime)) + ", Leave time: " + new java.text.SimpleDateFormat("dd-MM-yyyy | HH:mm:ss").format(new java.util.Date(leaveTime)) + ", Session time: " + sessionTime);
                }

                if (sessionTime > 0) {
                    plugin.getDatabaseManager().addPlaySession(playerUUID, playerName, sessionTime);
                } else {
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().severe("[DEBUG] Ignored session for player " + playerName + " due to non-positive session time: " + sessionTime);
                    }
                }
            } else {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().severe("[DEBUG] No join time recorded for player " + player.getName() + " on quit.");
                }
            }
        } else {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[DEBUG] Player " + player.getName() + " does not have the required permission to be tracked on quit.");
            }
        }
    }
}
