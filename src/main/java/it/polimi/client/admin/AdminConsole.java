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
import java.util.Map;

import static java.lang.System.exit;

public class AdminConsole extends Client {
    private final Map<String, RemoteServerInterface> raft;

    public AdminConsole() {
        this("admin1");
    }
    
    public AdminConsole(String adminName) {
        super(adminName);
        
        raft = new HashMap<>();        
        String[] servers = availableServers();
        for(String id : servers) {
            System.out.println("Connecting to " + id);
            try {
                raft.put(id, (RemoteServerInterface) registry.lookup(id));
            } catch (RemoteException | NotBoundException e) {
                System.err.println("Cannot connect to server " + id);
                e.printStackTrace();
            }
        }       
        
        if(!isAdmin) {
            System.err.println("You do not have admin rights. Terminating.");
            exit(-1);
        }
    }

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
                    System.out.println("\t'c': enter cluster management mode");
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
                    if(!isAdmin) {
                        System.out.println("Unrecognized command '" + choice + "'");
                    }
                    else {
                        clusterCmd();
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
     * Enters the cluster management menu
     */
    private void clusterCmd() {
        System.out.println("Raft - Cluster management mode");

        String[] params;
        String choice;
        while (true) {
            params = readCommand();
            try {
                choice = params[0];
            } catch(ArrayIndexOutOfBoundsException | NullPointerException e) {
                choice = "";
            }
            switch (choice) {
                case "h" -> {
                    System.out.println("Available cluster commands:");
                    System.out.println("\t'a [serverName]': adds a new configuration for the cluster");
                    System.out.println("\t'r [serverName]': removes the server configuration for the cluster");
                    System.out.println("\t'e [serverName]': exits to main menu");
                }
                // todo implement
                case "e" -> {
                    return;
                }
            }
        }
    }
    
    public void startServer(String serverName) {
        try {
            ProcessStarter.startServerProcess(serverName, 0, false);
            // todo add to raft?
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
    
    public void killServer(String serverName) {
        try {
            RemoteServerInterface toKill = (RemoteServerInterface) registry.lookup(serverName);
            toKill.stop();
        } catch (ConnectException | NullPointerException ex) {
            System.err.println("Cannot connect to " +serverName + ", server is likely not active");
        }
        catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }
    }
    
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

        RemoteServerInterface leader = raft.get("server3");        
        try {
            leader.changeConfiguration("admin1", 0, cNew);
        } catch (RemoteException | NotLeaderException e) {
            e.printStackTrace();
        }
    }
}
