package it.polimi.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.polimi.client.user.UserConfiguration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

public abstract class Client implements Remote, Serializable {
    protected final static String TIMEOUT = "10000"; // 10 seconds

    protected String id;
    
    protected BufferedReader br;
    protected Registry registry;

    protected InetAddress raftRegistryIp;
    protected Integer raftRegistryPort;
    protected boolean isAdmin;

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
    }
    
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
