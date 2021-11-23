package it.polimi.server.state;

import java.util.HashMap;
import java.util.Map;

public class Leader extends State {
    // Volatile state on leaders (Reinitialized after election):
    /**
     * For each server, index of the next log entry to send to that server (initialized to leader last log index + 1)
     */
    private Map<Integer, Integer> nextIndex;

    /**
     * for each server, index of highest log entry known to be replicated on server
     * (initialized to 0, increases monotonically)
     */
    private Map<Integer, Integer> matchIndex;

    public Leader() {
        super();
        nextIndex = new HashMap<>();
        matchIndex = new HashMap<>();
    }
}
