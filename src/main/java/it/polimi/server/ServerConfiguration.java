package it.polimi.server;

import lombok.Getter;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * Class used to read server configuration files
 */
@Getter
public class ServerConfiguration {
    /**
     * The server id
     */
    private final String name;
    /**
     * Port where to bind the server
     */
    private final Integer port;
    /**
     * Address where to find the registry
     */
    private final InetAddress registryIP;
    /**
     * Port of the registry
     */
    private final Integer registryPort;
    /**
     * List of servers in cluster
     */
    private final List<ServerConfiguration> cluster;

    public ServerConfiguration(String name, Integer port, InetAddress registryIP, Integer registryPort, List<ServerConfiguration> cluster) {
        this.name = name;
        this.port = port;
        this.registryIP = registryIP;
        this.registryPort = registryPort;
        this.cluster = cluster;
    }
}
