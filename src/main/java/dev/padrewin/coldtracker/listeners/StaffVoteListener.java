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

public class StaffVoteListener implements Listener {

    private final ColdTracker plugin;

    public StaffVoteListener(ColdTracker plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVote(VotifierEvent event) {
        final Vote vote = event.getVote();
        final String username = vote.getUsername();

        if (username == null) {
            debugWarn("Received a vote with no username.");
            return;
        }

        final String name = username.trim();
        if (name.isEmpty() || !name.matches("^[A-Za-z0-9_]{3,16}$")) {
            debugInfo("Ignoring vote with invalid username: " + name);
            return;
        }


        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            if (online.hasPermission("coldtracker.tracktime")) {
                logVote(online.getUniqueId(), online.getName(), vote);
            } else {
                debugInfo("Vote from " + name + " ignored (online but no staff perm).");
            }
            return;
        }


        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {


            UUID cachedUuid = null;
            try {
                OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name); // Paper API
                if (cached != null) {
                    cachedUuid = cached.getUniqueId();
                }
            } catch (NoSuchMethodError ignored) {

            }

            if (cachedUuid != null) {
                handleUuidPermissionAndLog(cachedUuid, name, vote);
                return;
            }


            plugin.getLuckPerms().getUserManager().lookupUniqueId(name).thenAccept(uuid -> {
                if (uuid == null) {
                    debugInfo("Ignoring vote from " + name + " (no cache, no LP match).");
                    return;
                }
                handleUuidPermissionAndLog(uuid, name, vote);
            }).exceptionally(ex -> {
                plugin.getLogger().severe("[ERROR] LuckPerms lookupUniqueId failed for " + name + ": " + ex.getMessage());
                return null;
            });
        });
    }

    private void handleUuidPermissionAndLog(UUID uuid, String suggestedName, Vote vote) {
        plugin.getLuckPerms().getUserManager().loadUser(uuid).thenAccept(user -> {
            if (user == null) {
                debugInfo("LP loadUser returned null for " + suggestedName + " (" + uuid + ")");
                return;
            }

            boolean isStaff = user.getCachedData().getPermissionData()
                    .checkPermission("coldtracker.tracktime").asBoolean();

            if (!isStaff) {
                debugInfo("Vote from " + suggestedName + " ignored (no staff perm).");
                return;
            }

            String resolvedName = user.getUsername() != null ? user.getUsername() : suggestedName;
            logVote(uuid, resolvedName, vote);
        }).exceptionally(ex -> {
            plugin.getLogger().severe("[ERROR] LuckPerms loadUser failed for " + suggestedName + " (" + uuid + "): " + ex.getMessage());
            return null;
        });
    }

    private void logVote(UUID playerUUID, String username, Vote vote) {
        String serviceName = vote.getServiceName() != null ? vote.getServiceName() : "unknown";
        String timestamp = vote.getTimeStamp() != null ? vote.getTimeStamp() : String.valueOf(System.currentTimeMillis());

        debugInfo("Logging vote for " + username + " from service " + serviceName + " at " + timestamp);

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
    }

    private void debugInfo(String msg) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[DEBUG] " + msg);
        }
    }

    private void debugWarn(String msg) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().warning("[DEBUG] " + msg);
        }
    }
}
