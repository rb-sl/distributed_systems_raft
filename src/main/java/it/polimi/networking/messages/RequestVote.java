package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RequestVote extends Message{
    private Integer requestNumber;
    private int term;
    private String candidateId;
    private Integer lastLogIndex;
    private Integer lastLogTerm;

    public RequestVote(Integer requestNumber, RemoteServerInterface origin, int term, String candidateId,
                       Integer lastLogIndex, Integer lastLogTerm) {
        super.messageType = Type.RequestVote;
        super.requestNumber = requestNumber;
        super.origin = origin;
        this.requestNumber = requestNumber;
        this.candidateId = candidateId;
        this.term = term;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }
}
