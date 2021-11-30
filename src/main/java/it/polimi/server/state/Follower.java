package it.polimi.server.state;

import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class Follower extends State {

    /**
     * Election timer
     */
    private final Timer electionTimer;

    /**
     * Init constructor
     * @param server The server
     */
    public Follower(Server server) {
        super(server);
        electionTimer = new Timer();
        startTimer();
    }

    /**
     * Parametric constructor for follower.
     */
    public Follower(Server server, int currentTerm, Integer votedFor, Logger logger, int commitIndex, int lastApplied, Map<String, Integer> variables) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied, variables);
        electionTimer = new Timer();
        startTimer();
    }

    private void startTimer() {
        // If election timeout elapses without receiving AppendEntries RPC from current
        // leader or granting vote to candidate: convert to candidate
        electionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                server.updateState(new Candidate(server, currentTerm, votedFor, logger,
                        commitIndex, lastApplied, variables));
                System.out.println("[!] Changed to CANDIDATE");
            }
        }, ELECTION_TIMEOUT);
    }

    public void onKeepAlive() {
        electionTimer.purge();
        startTimer();
    }

    @Override
    public void receivedMsg(int term) {
        super.receivedMsg(term);
        onKeepAlive();
    }
}
