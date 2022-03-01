package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import lombok.Getter;
import lombok.Setter;

/**
 * RequestVote message as per the paper
 */
@Getter
public class RequestVote extends Message{
    /**
     * Candidate’s term
     */
    private final int term;
    /**
     * Candidate requesting vote
     */
    private final String candidateId;
    /**
     * Index of candidate’s last log entry (§5.4)
     */
    private final Integer lastLogIndex;
    /**
     * Term of candidate’s last log entry (§5.4)
     */
    private final Integer lastLogTerm;

    public RequestVote(Integer requestNumber, RemoteServerInterface origin, int term, String candidateId,
                       Integer lastLogIndex, Integer lastLogTerm) {
        super.messageType = Type.RequestVote;
        super.internalRequestNumber = requestNumber;
        super.origin = origin;
        
        this.candidateId = candidateId;
        this.term = term;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }
}
