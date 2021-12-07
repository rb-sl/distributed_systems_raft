package it.polimi.networking.messages;

import lombok.Getter;

@Getter
public class StartElection extends Message {
    private int term;
    private Integer lastLogIndex;
    private Integer lastLogTerm;

    public StartElection(int term, Integer lastLogIndex, Integer lastLogTerm) {
        super.messageType = Type.StartElection;
        this.term = term;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }
}
