package it.polimi.client;

import lombok.Getter;

import java.net.InetAddress;

/**
 * Class to read client configuration json files
 */
@Getter
public class ClientConfiguration {
    /**
     * The client name
     */
    private final String name;
    /**
     * The address where the RMI registry is hosted
     */
    private final InetAddress raftRegistryIP;
    /**
     * The port where the registry is available
     */
    private final Integer raftRegistryPort;
    /**
     * Describes whether the client is an administrator
     */
    private final Boolean isAdmin;

    public ClientConfiguration(String name, InetAddress raftRegistryIP, Integer raftRegistryPort, Boolean isAdmin) {
        this.name = name;
        this.raftRegistryIP = raftRegistryIP;
        this.raftRegistryPort = raftRegistryPort;
        this.isAdmin = isAdmin;
    }
}
