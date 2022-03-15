package it.polimi.server;

import lombok.Getter;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.*;

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
    private final Map<String, ServerConfiguration> cluster;
    /**
     * Length of log after which a snapshot is taken
     */
    private final Integer maxLogLength;

    public ServerConfiguration(String name, Integer port, InetAddress registryIP, Integer registryPort, Map<String, ServerConfiguration> cluster, Integer maxLogLength) {
        this.name = name;
        this.port = port;
        this.registryIP = registryIP;
        this.registryPort = registryPort;
        this.cluster = cluster;
        this.maxLogLength = maxLogLength;
    }
    
    public static Map<String, ServerConfiguration> merge(Map<String, ServerConfiguration> conf1, Map<String, ServerConfiguration> conf2) {
        Map<String, ServerConfiguration> newCluster = new HashMap<>();
        newCluster.putAll(conf1);
        newCluster.putAll(conf2);
        return newCluster;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (this == o) return true;        
        if(getClass() != o.getClass()) {
            if(o.getClass() == String.class) {
                return this.name.equals(o);
            }
            else {
                return false;
            }
        }
        ServerConfiguration that = (ServerConfiguration) o;
        return name.equals(that.name) && Objects.equals(port, that.port) && Objects.equals(registryIP, that.registryIP) 
                && Objects.equals(registryPort, that.registryPort) && Objects.equals(cluster, that.cluster) 
                && Objects.equals(maxLogLength, that.maxLogLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, port, registryIP, registryPort, cluster, maxLogLength);
    }
}
