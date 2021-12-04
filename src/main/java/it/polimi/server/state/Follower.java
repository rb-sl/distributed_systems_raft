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
    private Timer electionTimer;

    private static Integer x = 42;

    private static Boolean rec;

    /**
     * Init constructor
     * @param server The server
     */
    public Follower(Server server) {
        super(server);
        System.out.println(Thread.currentThread().getId() + " [!] Starting as FOLLOWER");
        electionTimer = new Timer();
//        startTimer();
        waitTimeout();
    }

    /**
     * Parametric constructor for follower.
     * @see State#State(Server, int, Integer, Logger, int, int, Map<String, Integer>)
     */
    public Follower(Server server, int currentTerm, Integer votedFor, Logger logger, int commitIndex, int lastApplied, Map<String, Integer> variables) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied, variables);
        System.out.println(Thread.currentThread().getId() + " [!] Changed to FOLLOWER");
        electionTimer = new Timer();
//        startTimer();
        waitTimeout();
    }

    private void waitTimeout() {
        while(true) {
            rec = false;
            synchronized (x) {
                try {
                    x.wait(ELECTION_TIMEOUT);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            if(!rec) {
                server.updateState(new Candidate(server, currentTerm, votedFor, logger,
                        commitIndex, lastApplied, variables));
                return;
            }
        }
    }

    /**
     * Starts election timer
     */
    private void startTimer() {
        // If election timeout elapses without receiving AppendEntries RPC from current
        // leader or granting vote to candidate: convert to candidate
        electionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                server.updateState(new Candidate(server, currentTerm, votedFor, logger,
                        commitIndex, lastApplied, variables));
            }
        }, ELECTION_TIMEOUT);
    }

    /**
     * Handler for keep-alive event. Restarts timer
     */
    public void onKeepAlive() {
        electionTimer.cancel();
        electionTimer.purge();
//        electionTimer = new Timer();
        startTimer();
    }

    /**
     * Like {@link State#receivedMsg(int)}, but calls {@link Follower#onKeepAlive()}
     * @param term The term of received message
     */
    @Override
    public void receivedMsg(int term) {
        super.receivedMsg(term);
        rec=true;


//        onKeepAlive();
    }
}
