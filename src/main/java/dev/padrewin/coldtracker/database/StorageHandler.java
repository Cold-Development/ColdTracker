package dev.padrewin.coldtracker.database;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface StorageHandler {

    void startBatchUpdater();
    void stopBatchUpdater(); // Added this method

    CompletableFuture<Long> getTotalTimeAsync(UUID playerUUID);
    CompletableFuture<Integer> getTotalVotesAsync(UUID playerUUID);
    CompletableFuture<Map<String, Long>> getPlaytimeByServer(UUID playerUUID);

    void logJoinTime(UUID playerUUID, String playerName, long joinTime);
    CompletableFuture<Void> removeJoinTimeAsync(UUID playerUUID);
    void addPlaySession(UUID playerUUID, long sessionTime);
    void addVote(UUID playerUUID, String playerName, String serviceName, String timestamp);

    void wipeDatabaseTables();
    void cleanupStaleSessions();
    void closeConnection();

    CompletableFuture<Set<UUID>> getAllPlayerUUIDs();
    CompletableFuture<List<VoteRecord>> getAllVotes();
}