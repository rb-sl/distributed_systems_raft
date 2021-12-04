package it.polimi.server.state;

import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.util.Map;
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
    private static Integer x = 42;

    /**
     * Number of received votes
     */
    private int nVotes;

    private Boolean receivedAppend;

    public Candidate(Server server, int currentTerm, Integer votedFor, Logger logger, int commitIndex, int lastApplied, Map<String, Integer> variables) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied, variables);

        System.out.println(Thread.currentThread().getId() + " [!] Changed to CANDIDATE");
        this.electionTimer = new Timer();
        // On conversion to candidate, start election:
        startElection();
    }

    /**
     * Starts the election process
     */
    private synchronized void startElection() {
        while(true) {
            super.currentTerm++;

            receivedAppend = false;

            System.out.println("[ELECTION] Starting new election in term " + currentTerm);

            // Vote for self
            nVotes = 0;
            incrementVotes();
            super.setVotedFor(this.server.getId());

            // Reset election timer
//            startTimer();

            // Send RequestVote RPCs to all other servers
            this.server.startElection();

            try {
                synchronized(x) {
                    x.wait(ELECTION_TIMEOUT);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            if (receivedAppend) {
                this.server.updateState(new Follower(server, this.currentTerm, this.votedFor, this.logger,
                        this.commitIndex, this.lastApplied, this.variables));
                return;
            } else if (nVotes > server.getClusterSize() / 2) {
                this.server.updateState(new Leader(server, currentTerm, votedFor, logger,
                        commitIndex, lastApplied, variables));
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
//                    startElection();
                    synchronized (x) {
                        x.notifyAll();
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

            synchronized (x) {
                x.notifyAll();
            }
//            this.server.updateState(new Leader(server, currentTerm, votedFor, logger,
//                       commitIndex, lastApplied, variables));
        }
        System.out.println("Votes received: " + nVotes);
    }

    @Override
    public void receivedAppend(int term) {
        electionTimer.cancel();
        electionTimer.purge();

        receivedAppend=true;

        synchronized (x) {
            x.notifyAll();
        }
//        currentTerm = term;
//        this.server.updateState(new Follower(server, this.currentTerm, this.votedFor, this.logger,
//                this.commitIndex, this.lastApplied, this.variables));
    }
}
