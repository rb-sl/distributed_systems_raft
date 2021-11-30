package it.polimi.server.state;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.Result;
import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

public class Candidate extends State {

    /**
     * Election timer
     */
    private final Timer electionTimer;

    /**
     * Number of received votes
     */
    private int nVotes;

    public Candidate(Server server, int currentTerm, Integer votedFor, Logger logger, int commitIndex, int lastApplied, Map<String, Integer> variables) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied, variables);
        this.electionTimer = new Timer();
        // On conversion to candidate, start election:
        startElection();
    }

    /**
     * Starts the election process
     */
    private void startElection() {
        // todo fix
//        float timeout;
//        try {
//            while (true) {
                // Increment currentTerm
                super.currentTerm++;

                // Vote for self
                nVotes = 1;

                // Reset election timer
                startTimer();

                // Send RequestVote RPCs to all other servers

                // Start threads for requesting
                this.server.requestElection(Thread.currentThread());

                // Sleep for randomized timeout between ELECTION_TIMEOUT/2 and ELECTION_TIMEOUT
//                timeout = 0.5F + new Random().nextFloat() * 0.5F;
//                TimeUnit.MILLISECONDS.sleep((long) timeout * ELECTION_TIMEOUT);
//            }
//        } catch (InterruptedException e) {
//            if(nVotes > server.getClusterSize() / 2) {
//                server.updateState(new Leader(server, currentTerm, votedFor, logger,
//                        commitIndex, lastApplied, variables));
//            } else {
//                server.updateState(new Follower(server, currentTerm, votedFor, logger,
//                        commitIndex, lastApplied, variables));
//            }
//        }
    }

    /**
     * Starts election timer
     */
    private void startTimer() {
        // If election timeout elapses: start new election
        electionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                startElection();
            }
        }, ELECTION_TIMEOUT);
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void incrementVotes(Thread starter) {
        nVotes++;
        if(nVotes > server.getClusterSize() / 2) {
            // Stops on win
            starter.interrupt();
        }
    }
}
