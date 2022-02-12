package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import lombok.Getter;

@Getter
public class InstallSnapshot extends Message {
    private final int term;
    private final RemoteServerInterface origin;
    private final String leaderId;
    private final Integer lastIncludedIndex;
    private final Integer lastIncludedTerm;
    private final int offset;
    private final byte[] data;
    private final boolean done;

    public InstallSnapshot(Integer requestNumber, RemoteServerInterface origin, int term, String leaderId,
                           Integer lastIncludedIndex, Integer lastIncludedTerm, int offset, byte[] data,
                           boolean done) {
        super.messageType = Type.InstallSnapshot;
        super.internalRequestNumber = requestNumber;
        super.origin = origin;
        this.term = term;
        this.origin = origin;
        this.leaderId = leaderId;
        this.lastIncludedIndex = lastIncludedIndex;
        this.lastIncludedTerm = lastIncludedTerm;
        this.offset = offset;
        this.data = data;
        this.done = done;
    }
}
