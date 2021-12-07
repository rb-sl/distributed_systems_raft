package it.polimi.server;

import lombok.Getter;

import java.net.InetAddress;
import java.util.List;
import java.util.Map;

@Getter
public class ServerConfiguration {
    private String name;
    private Integer port;

    private InetAddress registryIP;
    private Integer registryPort;

    private List<ServerConfiguration> cluster;
    private List<InetAddress> knownAddresses;

    private Map<String, Integer> variables;

    public ServerConfiguration(String name, Integer port, InetAddress registryIP, Integer registryPort,
                               List<ServerConfiguration> cluster, List<InetAddress> knownAddresses,
                               Map<String, Integer> variables) {
        this.name = name;
        this.port = port;
        this.registryIP = registryIP;
        this.registryPort = registryPort;
        this.cluster = cluster;
        this.knownAddresses = knownAddresses;
        this.variables = variables;
    }
}
