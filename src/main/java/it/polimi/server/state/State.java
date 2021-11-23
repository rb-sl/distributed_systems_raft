package it.polimi.server.state;

import it.polimi.server.log.Logger;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

public abstract class State {
    // Persistent state on all servers (Updated on stable storage before responding to RPCs)
    /**
     * Latest term server has seen (initialized to 0 on first boot, increases monotonically)
     */
    @Getter @Setter
    private int currentTerm;
    /**
     * CandidateId that received vote in current term (or null if none)
     */
    @Getter @Setter
    private Integer votedFor;
    /**
     * Log entries; each entry contains command for state machine, and term when entry was received by leader (first index is 1)
     */
    @Getter @Setter
    private Logger logger;

    // Volatile state on all servers:

    /**
     * Index of highest log entry known to be committed (initialized to 0, increases monotonically)
     */
    @Getter @Setter
    private int commitIndex;

    /**
     * Index of highest log entry applied to state machine (initialized to 0, increases monotonically)
     */
    @Getter @Setter
    private int lastApplied;

    public State() {
        this.setCommitIndex(0);
        this.setCurrentTerm(-1);
        this.setVotedFor(null);
        this.setLogger(new Logger());

        this.setCommitIndex(0);
        this.setLastApplied(0);
    }

    /**
     * Increase current term value
     */
    public void increaseCurrentTerm() {
        this.currentTerm++;
    }
}
