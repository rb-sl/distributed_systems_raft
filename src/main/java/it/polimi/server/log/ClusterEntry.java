package it.polimi.server.log;

/**
 * Special LogEntry to store cluster changes
 */
public class ClusterEntry extends LogEntry {
    
    public ClusterEntry(int term, String varName, Integer value, Integer internalRequestNumber, Integer clientRequestNumber, Integer index) {
        super(term, varName, value, internalRequestNumber, clientRequestNumber, index);
    }
}
