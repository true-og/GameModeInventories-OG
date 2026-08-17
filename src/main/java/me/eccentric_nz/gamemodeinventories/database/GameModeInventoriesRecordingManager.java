package me.eccentric_nz.gamemodeinventories.database;

public class GameModeInventoriesRecordingManager {

    // Counts skipped recorder runs: after x skips in a row the recorder delays
    // itself so it does not kill the server.
    public static int failedDbConnectionCount = 0;

}
