package it.polimi.server.log;

import it.polimi.server.ServerConfiguration;
import lombok.Getter;

import java.util.Map;

/**
 * Special LogEntry to store cluster changes
 */
@Getter
public class ClusterEntry extends LogEntry {
    /**
     * New configuration
     */
    private final Map<String, ServerConfiguration> configuration;
    
    public ClusterEntry(int term, Integer clientRequestNumber, Map<String, ServerConfiguration> configuration, Integer index) {
        super(term, clientRequestNumber, index);
        this.configuration = configuration;
    }
}
