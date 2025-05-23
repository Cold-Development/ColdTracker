package dev.padrewin.coldtracker.database;

import java.util.UUID;

public class VoteRecord {
    private final UUID playerUUID;
    private final String playerName;
    private final String serviceName;
    private final String voteTime;

    public VoteRecord(UUID playerUUID, String playerName, String serviceName, String voteTime) {
        this.playerUUID = playerUUID;
        this.playerName = playerName;
        this.serviceName = serviceName;
        this.voteTime = voteTime;
    }

    public UUID getPlayerUUID() { return playerUUID; }
    public String getPlayerName() { return playerName; }
    public String getServiceName() { return serviceName; }
    public String getVoteTime() { return voteTime; }
}