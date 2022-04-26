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
     * Public address where to find the server and its registry
     */
    private final InetAddress serverIP;
    /**
     * Port where to bind the server
     */
    private final Integer port;
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

    public ServerConfiguration(String name, InetAddress serverIP, Integer port, Integer registryPort, Map<String, ServerConfiguration> cluster, Integer maxLogLength) {
        this.name = name;
        this.serverIP = serverIP;
        this.port = port;
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
    
    public Integer getRegistryPort() {
        return this.registryPort != null ? this.registryPort : 1099;
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
        return name.equals(that.name) && Objects.equals(port, that.port) && Objects.equals(serverIP, that.serverIP) 
                && Objects.equals(registryPort, that.registryPort) && Objects.equals(cluster, that.cluster) 
                && Objects.equals(maxLogLength, that.maxLogLength);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, port, serverIP, registryPort, cluster, maxLogLength);
    }
}
