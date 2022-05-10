package it.polimi.client.admin;

import com.google.gson.reflect.TypeToken;
import it.polimi.client.Client;
import it.polimi.exceptions.NotLeaderException;
import it.polimi.networking.RemoteServerInterface;
import it.polimi.server.ServerConfiguration;
import it.polimi.utilities.ProcessStarter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.System.exit;

public class Admin extends Client {
    /**
     * Map of the cluster
     */
    private final Map<String, RemoteServerInterface> raftCluster;

    public Admin() {
        this("admin1");
    }
    
    public Admin(String adminName) {
        super(adminName);

        raftCluster = new HashMap<>();        
        List<String> servers = availableServers();
        for(String id : servers) {
            System.out.println("Connecting to " + id);
            try {
                raftCluster.put(id, getServerInterface(id));
            } catch (RemoteException | NotBoundException e) {
                System.err.println("Cannot connect to server " + id);
            }
        }       
        
        if(!isAdmin) {
            System.err.println("You do not have admin rights. Terminating.");
            exit(-1);
        }
    }

    /**
     * Starts the interactive shell for the client
     */
    public void startCmd() {
        System.out.println("Raft console - Admin mode");
        System.out.println("Use 'h' to see available commands");

        br = new BufferedReader(new InputStreamReader(System.in));
        String[] params;
        String choice;
        while (true) {
            params = readCommand();
            try {
                choice = params[0];
            } catch (ArrayIndexOutOfBoundsException | NullPointerException e) {
                choice = "";
            }
            switch (choice) {
                case "h" -> {
                    System.out.println("Available commands:");
                    System.out.println("\t's [serverName]': starts the server with the given name on this machine (must exist in configuration)");
                    System.out.println("\t'k [serverName]': kills the server with the given name (must be active)");
                    System.out.println("\t'c [fileName]': send the given configuration");
                    System.out.println("\t'h': Opens this menu");
                    System.out.println("\t'q': Stops the client");
                }
                case "s" -> {
                    if (params.length != 2) {
                        System.out.println("Malformed command. Start must be in the form: 's [serverName]'");
                    }
                    else {
                        startServer(params[1]);
                    }
                }
                case "k" -> {
                    if (params.length != 2) {
                        System.out.println("Malformed command. Kill must be in the form: 'k [serverName]'");
                    }
                    else {
                        killServer(params[1]);
                    }
                }
                case "c" -> {
                    if (params.length != 2) {
                        System.out.println("Malformed command. Cluster change must be in the form: 'c [fileName]'");
                    }
                    else {
                        sendConfiguration(params[1]);
                    }
                }
                case "q" -> {
                    return;
                }
                default -> System.out.println("Unrecognized command '" + choice + "'");
            }
        }
    }
    
    /**
     * Starts a new process on the admin's machine for the given server
     * @param serverName Id of the server to start
     */
    public void startServer(String serverName) {
        try {
            ProcessStarter.startServerProcess(serverName, 0, false);
            System.out.println("Server " + serverName + " started.");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Stops the given server
     * @param serverName Id of the server to stop
     */
    public void killServer(String serverName) {
        try {
            getServerInterface(serverName).stop();
            System.out.println("Server " + serverName + " stopped.");
        } catch (ConnectException | NullPointerException ex) {
            System.err.println("Cannot connect to " + serverName + ", server is likely not active");
        }
        catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Starts a configuration change with the cluster's leader
     * @param fileName The name of the file containing the configuration 
     */
    public void sendConfiguration(String fileName) {
        Map<String, ServerConfiguration> cNew;
        try {
            Path storage = Paths.get("./client_configuration/" + fileName + ".json");
            Type type = new TypeToken<Map<String, ServerConfiguration>>() {}.getType();
            cNew = gson.fromJson(Files.readString(storage), type);
        } catch (NoSuchFileException e) {
            System.err.println("Cannot find configuration for client configuration. Terminating.");
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        boolean requestComplete = false;
        String newLeaderException = null;
        while (!requestComplete) {
            try {
                raft.changeConfiguration(id, this.requestSerialnumber, cNew);
                this.requestSerialnumber++;
                requestComplete = true;
            } catch (NotLeaderException e) {
                if(!e.toString().equals(newLeaderException)) {
                    System.err.println(e + " Connecting to leader");
                    raft = e.getLeader();
                    newLeaderException = e.toString();
                }
                else {
                    System.err.println(e + " Retrying...");
                    raft = connectToRandomServer();
                }                
            } catch (RemoteException | NullPointerException e) {
                System.err.println("Connection error, retrying...");
                raft = connectToRandomServer();
            }
        }

        System.out.println("Configuration " + fileName + " installed.");
    }
}
