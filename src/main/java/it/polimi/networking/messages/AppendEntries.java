package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.server.log.LogEntry;
import lombok.Getter;

import java.util.SortedMap;

/**
 * Message for the AppendEntries RPC as described in the paper
 */
@Getter
public class AppendEntries extends Message {
    /**
     * Leader’s term
     */
    private final Integer term;
    /**
     * So follower can redirect clients
     */
    private final String leaderId;
    /**
     * Index of log entry immediately preceding new ones
     */
    private final Integer prevLogIndex;
    /**
     * Term of prevLogIndex entry
     */
    private final Integer prevLogTerm;
    /**
     * Log entries to store (empty for heartbeat; may send more than one for efficiency)
     */
    private final SortedMap<Integer, LogEntry> newEntries;
    /**
     * Leader’s commitIndex
     */
    private final Integer leaderCommit;

    public AppendEntries(Integer requestNumber, RemoteServerInterface origin, int term, String leaderId,
                         Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> newEntries,
                         Integer leaderCommit) {
        super.messageType = Type.AppendEntry;
        super.internalRequestNumber = requestNumber;
        super.origin = origin;
        
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.newEntries = newEntries;
        this.leaderCommit = leaderCommit;
    }
}
