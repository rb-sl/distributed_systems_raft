package it.polimi.client;

import it.polimi.server.ServerConfiguration;
import lombok.Getter;

import java.util.Map;

/**
 * Class to read client configuration json files
 */
@Getter
public class ClientConfiguration {
    /**
     * The client name
     */
    private final String name;
    
    protected Map<String, ServerConfiguration> clusterConfiguration;
    /**
     * Describes whether the client is an administrator
     */
    private final Boolean isAdmin;

    public ClientConfiguration(String name, Map<String, ServerConfiguration> clusterConfiguration, Boolean isAdmin) {
        this.name = name;
        this.clusterConfiguration = clusterConfiguration;
        this.isAdmin = isAdmin;
    }
}
