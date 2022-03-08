package it.polimi.server;

import lombok.Getter;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Class used to read server configuration files
 */
@Getter
public class ServerConfiguration implements Serializable {
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
    /**
     * Length of log after which a snapshot is taken
     */
    private final Integer maxLogLength;

    public ServerConfiguration(String name, Integer port, InetAddress registryIP, Integer registryPort, List<ServerConfiguration> cluster, Integer maxLogLength) {
        this.name = name;
        this.port = port;
        this.registryIP = registryIP;
        this.registryPort = registryPort;
        this.cluster = cluster;
        this.maxLogLength = maxLogLength;
    }
    
    public static ServerConfiguration merge(ServerConfiguration conf1, ServerConfiguration conf2) {
        List<ServerConfiguration> newCluster = new ArrayList<>();
        newCluster.addAll(conf1.cluster);
        newCluster.addAll(conf2.cluster);
        return new ServerConfiguration(conf2.name, conf2.port, conf2.registryIP, conf2.registryPort, newCluster, conf2.maxLogLength);
    }
}
