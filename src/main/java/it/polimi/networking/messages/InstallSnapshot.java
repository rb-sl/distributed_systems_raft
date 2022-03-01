package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import lombok.Getter;

/**
 * Message for the InstallSnapshot RPC
 */
@Getter
public class InstallSnapshot extends Message {
    /**
     * Leader’s term
     */
    private final int term;
    /**
     * So follower can redirect clients
     */
    private final String leaderId;
    /**
     * The snapshot replaces all entries up through and including this index
     */
    private final Integer lastIncludedIndex;
    /**
     * Term of lastIncludedIndex
     */
    private final Integer lastIncludedTerm;
    /**
     * Byte offset where chunk is positioned in the
     * snapshot file
     */
    private final int offset;
    /**
     * Raw bytes of the snapshot chunk, starting at offset
     */
    private final byte[] data;
    /**
     * True if this is the last chunk
     */
    private final boolean done;

    public InstallSnapshot(Integer requestNumber, RemoteServerInterface origin, int term, String leaderId,
                           Integer lastIncludedIndex, Integer lastIncludedTerm, int offset, byte[] data,
                           boolean done) {
        super.messageType = Type.InstallSnapshot;
        super.internalRequestNumber = requestNumber;
        super.origin = origin;
        
        this.term = term;
        this.leaderId = leaderId;
        this.lastIncludedIndex = lastIncludedIndex;
        this.lastIncludedTerm = lastIncludedTerm;
        this.offset = offset;
        this.data = data;
        this.done = done;
    }
}
