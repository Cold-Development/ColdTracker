package dev.padrewin.coldtracker.integration;

public record SanctionCounts(int mutes, int bans, int kicks, int warnings) {

    public static final SanctionCounts EMPTY = new SanctionCounts(0, 0, 0, 0);

    public int total() {
        return mutes + bans + kicks + warnings;
    }
}
