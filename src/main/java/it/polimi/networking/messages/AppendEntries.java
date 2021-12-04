package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.server.log.LogEntry;
import lombok.Getter;
import lombok.Setter;

import java.util.SortedMap;

@Getter @Setter
public class AppendEntries extends Message {
    private int term;
    private Integer leaderId;
    private Integer prevLogIndex;
    private Integer prevLogTerm;
    private SortedMap<Integer, LogEntry> newEntries;
    private Integer leaderCommit;

    public AppendEntries(Integer requestNumber, RemoteServerInterface origin, int term, Integer leaderId,
                         Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> newEntries,
                         Integer leaderCommit) {
        super.messageType = Type.AppendEntry;
        super.requestNumber = requestNumber;
        super.origin = origin;
        this.term = term;
        this.leaderId = leaderId;
        this.prevLogIndex = prevLogIndex;
        this.prevLogTerm = prevLogTerm;
        this.newEntries = newEntries;
        this.leaderCommit = leaderCommit;
    }
}
