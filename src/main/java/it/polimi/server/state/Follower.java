package it.polimi.server.state;

import it.polimi.networking.messages.StateTransition;
import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ThreadLocalRandom;

/**
 * State for followers
 */
public class Follower extends State {
    /**
     * Election timer
     */
    private static Timer electionTimer;

    /**
     * Init constructor
     * @param server The server
     */
    public Follower(Server server, Integer maxLogLength) {
        super(server, maxLogLength);
        this.role = Role.Follower;
        System.out.println(Thread.currentThread().getId() + " [!] Starting as FOLLOWER");
        startTimer();
    }

    /**
     * Parametric constructor
     * @param state The previous state
     */
    public Follower(State state) {
        this(state.server, state.currentTerm, state.votedFor, state.logger, commitIndex, state.lastApplied);
    }

    /**
     * Parametric constructor for follower.
     * @see State#State(Server, Integer, String, Logger, Integer, Integer)
     */
    public Follower(Server server, Integer currentTerm, String votedFor, Logger logger, Integer commitIndex, Integer lastApplied) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied);
        this.role = Role.Follower;
        System.out.println(Thread.currentThread().getId() + " [!] Changed to FOLLOWER in Term " + currentTerm);
        startTimer();
    }

    /**
     * Starts election timer
     */
    private void startTimer() {
        // If election timeout elapses without receiving AppendEntries RPC from current
        // leader or granting vote to candidate: convert to candidate
        int timeout = ThreadLocalRandom.current().nextInt(MIN_ELECTION_TIMEOUT, ELECTION_TIMEOUT + 1);
        electionTimer = new Timer();
        try {
            electionTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    stopTimers();
                    server.enqueue(new StateTransition(Role.Candidate));
                }
            }, timeout);
        } catch(IllegalStateException e) {
            System.out.println("(Follower timer canceled)");
        }
    }

    /**
     * Handler for keep-alive event. Restarts timer
     */
    public void onKeepAlive() {
        stopTimers();
        startTimer();
    }

    /**
     * Like {@link State#receivedMsg(Integer)}, but calls {@link Follower#onKeepAlive()}
     * @param term The term of received message
     */
    @Override
    public void receivedMsg(Integer term) {
        super.receivedMsg(term);
        onKeepAlive();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopTimers() {
        super.stopTimers();
        electionTimer.cancel();
        electionTimer.purge();
    }
}
