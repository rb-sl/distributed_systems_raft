package it.polimi.networking.messages;

import lombok.Getter;

/**
 * Internal message to start a new election on timer expiration
 */
@Getter
public class StartElection extends Message {
    /**
     * Current term
     */
    private final int term;
    /**
     * The candidate's last index
     */
    private final Integer lastLogIndex;
    /**
     * Term of the last log index
     */
    private final Integer lastLogTerm;

    public StartElection(int term, Integer lastLogIndex, Integer lastLogTerm) {
        super.messageType = Type.StartElection;
        
        this.term = term;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }
}
