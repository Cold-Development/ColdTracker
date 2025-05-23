package dev.padrewin.coldtracker.listeners;

import dev.padrewin.coldtracker.ColdTracker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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

            plugin.getStorageHandler().logJoinTime(playerUUID, player.getName(), joinTime);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Player " + player.getName() + " joined. Logged join_time: " + joinTime);
            }
        } else {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Player " + player.getName() + " does not have the required permission to be tracked.");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (player.hasPermission("coldtracker.tracktime")) {
            UUID playerUUID = player.getUniqueId();

            plugin.getStorageHandler().removeJoinTimeAsync(playerUUID);

            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().info("[DEBUG] Player " + player.getName() + " quit. Processed session time.");
            }
        }
    }
}