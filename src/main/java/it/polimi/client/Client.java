package it.polimi.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.polimi.networking.RemoteServerInterface;
import it.polimi.server.ServerConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
     * Configuration of servers in the cluster
     */
    protected Map<String, ServerConfiguration> clusterConfiguration;
    
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
        
        ClientConfiguration clientConfiguration;
        try {
            Path storage = Paths.get("./client_configuration/" + clientName + ".json");
            Type type = new TypeToken<ClientConfiguration>(){}.getType();
            clientConfiguration = gson.fromJson(Files.readString(storage), type);
        } catch (NoSuchFileException e) {
            System.err.println("Cannot find configuration for client '" + clientName + "'. Terminating.");
            return;
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }

        this.clusterConfiguration = clientConfiguration.getClusterConfiguration();
        this.isAdmin = clientConfiguration.getIsAdmin() != null && clientConfiguration.getIsAdmin();

        requestSerialnumber = 0;
    }

    /**
     * Gets the list of servers on the registry
     * @return The list of server ids
     */
    protected List<String> availableServers() {
        return this.clusterConfiguration.keySet().stream().toList();         
    }
    
    protected RemoteServerInterface getServerInterface(String serverId) throws RemoteException, NotBoundException {
        ServerConfiguration serverConfiguration = this.clusterConfiguration.get(serverId);
        Registry registry = LocateRegistry.getRegistry(serverConfiguration.getServerIP().getHostAddress(), serverConfiguration.getRegistryPort());
        return (RemoteServerInterface) registry.lookup(serverId);
    }

    /**
     * Connects to a server available in the registry
     * @return The server's interface
     */
    protected RemoteServerInterface connectToRandomServer() {
        List<String> servers = availableServers();
        Random rand = new Random();

        String entryPoint = servers.get(rand.nextInt(servers.size()));
        System.out.println("Connecting to " + entryPoint);
        RemoteServerInterface raft = null;
        try {
            raft = getServerInterface(entryPoint);
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
            System.out.print("> ");
            String line;
            line = br.readLine();
            return line.split(" ");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
