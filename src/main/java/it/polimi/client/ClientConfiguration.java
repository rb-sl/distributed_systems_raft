package it.polimi.client;

import lombok.Getter;

import java.net.InetAddress;

@Getter
public class ClientConfiguration {
    private String name;
    private InetAddress raftRegistryIP;
    private Integer raftRegistryPort;

    private Boolean isAdmin;

    public ClientConfiguration(String name, InetAddress raftRegistryIP, Integer raftRegistryPort, Boolean isAdmin) {
        this.name = name;
        this.raftRegistryIP = raftRegistryIP;
        this.raftRegistryPort = raftRegistryPort;
        this.isAdmin = isAdmin;
    }
}
