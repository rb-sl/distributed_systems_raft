package it.polimi.server.state;

import it.polimi.networking.messages.Message;
import it.polimi.networking.messages.Result;
import it.polimi.networking.messages.StateTransition;
import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Timer;
import java.util.TimerTask;

public class Candidate extends State {

    /**
     * Election timer
     */
    private final Timer electionTimer;

    /**
     * Answer to the Ultimate Question of Life, the Universe, and Everything
     */
    private static final Object timeoutSync = new Object();

    /**
     * Number of received votes
     */
    private static int nVotes;

    /**
     * Whether the state received an append and need to change state
     */
    private static Boolean receivedAppend;

    public Candidate(State state) {
        this(state.server, state.currentTerm, state.votedFor, state.logger, state.commitIndex, state.lastApplied, state.variables);
    }

    public Candidate(Server server, int currentTerm, Integer votedFor, Logger logger, int commitIndex, int lastApplied, Map<String, Integer> variables) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied, variables);

        System.out.println(Thread.currentThread().getId() + " [!] Changed to CANDIDATE");
        this.electionTimer = new Timer();
        // On conversion to candidate, start election:
//        Thread t = new Thread(this::startElection);
//        t.start();
        startElection();
    }

    /**
     * Start the election process
     */
    private synchronized void startElectionRequest() {
        int lastLogTerm;
        try {
            lastLogTerm = this.getLogger().getLastIndex();
        } catch (NoSuchElementException e) {
            lastLogTerm = -1;
        }
        this.server.startElection(currentTerm, getLastLogIndex(), lastLogTerm);
    }

    /**
     * Starts the election process
     */
    private synchronized void startElection() {
        while(true) {
            super.currentTerm++;
            receivedAppend = false;

            System.out.println(Thread.currentThread().getId() + " [ELECTION] Starting new election in term " + currentTerm);

            nVotes = 0;

            // Reset election timer
            startTimer();

            // Vote for self
            super.setVotedFor(this.server.getId());
            incrementVotes();

            // Send RequestVote RPCs to all other servers
            Thread t = new Thread(this::startElectionRequest);
            t.start();
            startElectionRequest();

            try {
                synchronized(timeoutSync) {
                    timeoutSync.wait(ELECTION_TIMEOUT);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (receivedAppend || nVotes > server.getClusterSize() / 2) {
                return;
            }
        }
    }

    /**
     * Starts election timer
     */
    private void startTimer() {
        // If election timeout elapses: start new election
        try {
            electionTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    synchronized (timeoutSync) {
                        timeoutSync.notifyAll();
                    }
                }
            }, ELECTION_TIMEOUT);
        } catch (IllegalStateException e) {
            System.out.println("(Candidate timer canceled)");
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void incrementVotes() {
        nVotes++;
        if(nVotes > server.getClusterSize() / 2) {
            electionTimer.cancel();
            electionTimer.purge();

            synchronized (timeoutSync) {
                timeoutSync.notifyAll();
            }

            this.server.enqueue(new StateTransition(Role.Leader));
        }
        System.out.println("Votes received: " + nVotes + "/" + server.getClusterSize());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receivedAppend(int term) {
        electionTimer.cancel();
        electionTimer.purge();

        receivedAppend = true;

        synchronized (timeoutSync) {
            timeoutSync.notifyAll();
        }

        // Syncs to the leader's term
        currentTerm = term;
        this.server.enqueue(new StateTransition(Role.Follower));
    }

    /**
     * {@inheritDoc}
     */
    public void processResult(Message.Type type, Result result) {
        if (type == Message.Type.RequestVote) {
            if(result.isSuccess()) {
                incrementVotes();
            }
        }
    }
}
