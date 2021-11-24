package it.polimi.server.state;

import java.util.Timer;
import java.util.TimerTask;

public class Follower extends State {

    /**
     * The election timeout
     */
    private static final int ELECTION_TIMEOUT = 350;

    /**
     * Election timer
     */
    private final Timer electionTimer;

    public Follower() {
        super();
        electionTimer = new Timer();
        startTimer();
    }

    private void startTimer() {
        electionTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                updateState();
            }
        }, ELECTION_TIMEOUT);
    }

    public void onKeepAlive() {
        electionTimer.purge();
        startTimer();
    }

    private void updateState() {
        // TODO: to candidation and beyond
    }


}
