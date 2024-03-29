package it.polimi.server.state;

import it.polimi.networking.messages.Message;
import it.polimi.networking.messages.Result;
import it.polimi.networking.messages.StartElection;
import it.polimi.networking.messages.StateTransition;
import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * State for the candidate
 */
public class Candidate extends State {
    /**
     * Election timer
     */
    private Timer electionTimer;

    /**
     * Number of received votes
     */
    private static int nVotes;

    /**
     * If true stops the election
     */
    private static boolean elected;

    public Candidate(State state) {
        this(state.server, state.currentTerm, state.votedFor, state.logger, commitIndex, state.lastApplied);
    }

    public Candidate(Server server, Integer currentTerm, String votedFor, Logger logger, Integer commitIndex, Integer lastApplied) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied);
        super.role = Role.Candidate;
        elected = false;

        System.out.println(Thread.currentThread().getId() + " [!] Changed to CANDIDATE in Term " + currentTerm);

        // On conversion to candidate, start election:
        election();
    }

    /**
     * Starts the election process
     */
    private void election() {
        // Increment currentTerm
        if(currentTerm != null) {
            super.currentTerm++;
        }
        else {
            currentTerm = 0;
        }
        System.out.println(Thread.currentThread().getId() + " [ELECTION] Starting new election in term " + currentTerm);
        nVotes = 0;

        // Vote for self
        super.setVotedFor(this.server.getId());
        incrementVotes(currentTerm);

        // Reset election timer
        startTimer();

        Integer lastLogTerm;
        try {
            lastLogTerm = this.getLogger().getLastIndex();
        } catch (NoSuchElementException e) {
            lastLogTerm = null;
        }

        // Send RequestVote RPCs to all other servers
        this.server.enqueue(new StartElection(currentTerm, getLastLogIndex(), lastLogTerm));
    }

    /**
     * Starts election timer
     */
    private void startTimer() {
        // Randomized timeout
        electionTimer = new Timer();
        int timeout = ThreadLocalRandom.current().nextInt(MIN_ELECTION_TIMEOUT, ELECTION_TIMEOUT + 1);
        // If election timeout elapses: start new election
        try {
            electionTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    election();
                }
            }, timeout);
        } catch (IllegalStateException e) {
            e.printStackTrace();
            System.out.println("(Candidate timer canceled)");
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void incrementVotes(Integer term) {
        nVotes++;

        if(!elected && nVotes > server.getClusterSize() / 2) {
            elected = true;
            stopTimers();
            this.server.enqueue(new StateTransition(Role.Leader));
        }
        System.out.println("Votes received: " + nVotes + "/" + server.getClusterSize());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void receivedAppend(int term) {
        super.receivedAppend(term);
        stopTimers();

        // Syncs to the leader's term
        currentTerm = term;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void processResult(Message.Type type, Result result) {
        super.processResult(type, result);
        if (type == Message.Type.RequestVote) {
            if(result.isSuccess()) {
                incrementVotes(result.getTerm());
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void stopTimers() {
        super.stopTimers();
        if(electionTimer != null) {
            electionTimer.cancel();
            electionTimer.purge();
        }
    }
}
