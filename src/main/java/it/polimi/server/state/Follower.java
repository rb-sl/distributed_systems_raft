package it.polimi.server.state;

import it.polimi.networking.messages.StateTransition;
import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class Follower extends State {

    /**
     * Election timer
     */
    private static Timer electionTimer;

    /**
     * Init constructor
     * @param server The server
     */
    public Follower(Server server) {
        super(server);
        System.out.println(Thread.currentThread().getId() + " [!] Starting as FOLLOWER");
        startTimer();
    }

    /**
     * Parametric constructor
     * @param state The previous state
     */
    public Follower(State state) {
        this(state.server, state.currentTerm, state.votedFor, state.logger, state.commitIndex, state.lastApplied, state.variables);
    }

    /**
     * Parametric constructor for follower.
     * @see State#State(Server, int, Integer, Logger, int, int, Map<String, Integer>)
     */
    public Follower(Server server, int currentTerm, Integer votedFor, Logger logger, int commitIndex, int lastApplied, Map<String, Integer> variables) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied, variables);
        System.out.println(Thread.currentThread().getId() + " [!] Changed to FOLLOWER");
        startTimer();
    }

    /**
     * Starts election timer
     */
    private void startTimer() {
        // If election timeout elapses without receiving AppendEntries RPC from current
        // leader or granting vote to candidate: convert to candidate
        electionTimer = new Timer();
        electionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                electionTimer.cancel();
                electionTimer.purge();
                server.enqueue(new StateTransition(Role.Candidate));
            }
        }, ELECTION_TIMEOUT);
    }

    /**
     * Handler for keep-alive event. Restarts timer
     */
    public void onKeepAlive() {
        electionTimer.cancel();
        electionTimer.purge();
        startTimer();
    }

    /**
     * Like {@link State#receivedMsg(int)}, but calls {@link Follower#onKeepAlive()}
     * @param term The term of received message
     */
    @Override
    public void receivedMsg(int term) {
        super.receivedMsg(term);
        onKeepAlive();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopTimers() {
        electionTimer.cancel();
        electionTimer.purge();
    }
}
