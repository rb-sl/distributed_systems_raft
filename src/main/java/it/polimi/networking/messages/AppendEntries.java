package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.server.log.LogEntry;
import lombok.Getter;

import java.util.SortedMap;

@Getter
public class AppendEntries extends Message {
    private Integer term;
    private String leaderId;
    private Integer prevLogIndex;
    private Integer prevLogTerm;
    private SortedMap<Integer, LogEntry> newEntries;
    private Integer leaderCommit;

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
