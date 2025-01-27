package dev.padrewin.coldtracker.listeners;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import dev.padrewin.coldtracker.ColdTracker;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StaffVoteListener implements Listener {

    private final ColdTracker plugin;

    public StaffVoteListener(ColdTracker plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVote(VotifierEvent event) {
        Vote vote = event.getVote();
        String username = vote.getUsername();

        if (username == null || username.trim().isEmpty()) {
            if (plugin.getConfig().getBoolean("debug", false)) {
                plugin.getLogger().warning("[DEBUG] Received a vote with no username.");
            }
            return;
        }

        Player player = Bukkit.getPlayerExact(username);
        if (player != null && player.hasPermission("coldtracker.trackvote")) {
            logVote(player.getUniqueId(), player.getName(), vote);
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(username);

            if (!offlinePlayer.hasPlayedBefore()) {
                if (plugin.getConfig().getBoolean("debug", false)) {
                    plugin.getLogger().info("[DEBUG] Offline player " + username + " has never played on this server.");
                }
                return;
            }

            CompletableFuture<User> userFuture = plugin.getLuckPerms().getUserManager().loadUser(offlinePlayer.getUniqueId());
            userFuture.thenAccept(user -> {
                if (user != null && user.getCachedData().getPermissionData().checkPermission("coldtracker.trackvote").asBoolean()) {
                    logVote(offlinePlayer.getUniqueId(), username, vote);
                } else {
                    if (plugin.getConfig().getBoolean("debug", false)) {
                        plugin.getLogger().info("[DEBUG] Vote from " + username + " ignored (not staff or no permission).");
                    }
                }
            });
        });
    }

    private void logVote(UUID playerUUID, String username, Vote vote) {
        String serviceName = vote.getServiceName();
        String timestamp = vote.getTimeStamp();

        // Debug log
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] Logging vote for " + username + " from service " + serviceName + " at " + timestamp);
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                plugin.getDatabaseManager().addVote(
                        playerUUID,
                        username,
                        serviceName,
                        timestamp
                );
            } catch (Exception e) {
                plugin.getLogger().severe("[ERROR] Failed to log vote for " + username + ": " + e.getMessage());
            }
        });
    }

}
