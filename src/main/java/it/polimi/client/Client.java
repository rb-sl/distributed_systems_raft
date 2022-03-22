package it.polimi.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.polimi.client.user.UserConfiguration;
import it.polimi.networking.RemoteServerInterface;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ThreadLocalRandom;

import static java.lang.System.exit;

public abstract class Client implements Remote, Serializable {
    /**
     * Client timeout (ms)
     */
    protected final static String TIMEOUT = "10000";

    /**
     * Client id
     */
    protected String id;

    /**
     * Reader for the command line
     */
    protected BufferedReader br;
    
    /**
     * RMI registry of the cluster
     */
    protected Registry registry;

    /**
     * Registry ip, from configuration
     */
    protected InetAddress raftRegistryIp;
    /**
     * Registry port, from configuration
     */
    protected Integer raftRegistryPort;
    /**
     * Administration status, from configuration
     */
    protected boolean isAdmin;

    /**
     * Raft entry point
     */
    protected RemoteServerInterface raft;

    /**
     * Number of the client request
     */
    protected Integer requestSerialnumber;

    /**
     * Gson object
     */
    protected final Gson gson = new Gson();

    public Client(String clientName) {
        System.setProperty("sun.rmi.transport.tcp.responseTimeout", TIMEOUT);

        this.id = clientName;
        
        UserConfiguration userConfiguration;
        try {
            Path storage = Paths.get("./client_configuration/" + clientName + ".json");
            Type type = new TypeToken<UserConfiguration>(){}.getType();
            userConfiguration = gson.fromJson(Files.readString(storage), type);
        } catch (NoSuchFileException e) {
            System.err.println("Cannot find configuration for client '" + clientName + "'. Terminating.");
            return;
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }

        this.raftRegistryIp = userConfiguration.getRaftRegistryIP();
        this.raftRegistryPort = userConfiguration.getRaftRegistryPort();
        this.isAdmin = userConfiguration.getIsAdmin() != null && userConfiguration.getIsAdmin();

        requestSerialnumber = 0;

        // When a client first starts up, it connects to a randomly chosen server
        // If the client’s first choice is not the leader, that server will reject the client’s request and supply
        // information about the most recent leader [done in each method]
        raft = connectToRandomServer();
        if (raft == null) {
            System.err.println("Unable to access the Raft cluster");
            exit(0);
        }
    }

    /**
     * Gets the list of servers on the registry
     * @return The list of server ids
     */
    protected String[] availableServers() {
        try {
            registry = LocateRegistry.getRegistry(this.raftRegistryIp.getHostAddress(), this.raftRegistryPort);
            return registry.list();
        } catch (RemoteException e) {
            System.err.println("No registry available at " + this.raftRegistryIp + ":" + this.raftRegistryPort);
            return null;
        }        
    }

    /**
     * Connects to a server available in the registry
     * @return The server's interface
     */
    protected RemoteServerInterface connectToRandomServer() {
        String[] servers = availableServers();

        String entryPoint = servers[ThreadLocalRandom.current().nextInt(0, servers.length)];
        System.out.println("Connecting to " + entryPoint);
        RemoteServerInterface raft = null;
        try {
            raft = (RemoteServerInterface) registry.lookup(entryPoint);
        } catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }

        return raft;
    }

    /**
     * Reads a command from terminal
     * @return The split command
     */
    protected String[] readCommand() {
        try {
            String line;
            line = br.readLine();
            return line.split(" ");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
