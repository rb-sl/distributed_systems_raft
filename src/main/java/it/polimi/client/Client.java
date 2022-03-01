package it.polimi.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.polimi.exceptions.NotLeaderException;
import it.polimi.networking.RemoteServerInterface;
import it.polimi.utilities.ProcessStarter;

import java.io.*;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.ConnectException;
import java.rmi.NotBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.ThreadLocalRandom;

// TODO: move admin mode on a separate app
public class Client implements Remote, Serializable {
    private final static String TIMEOUT = "10000"; // 10 seconds
    private RemoteServerInterface raft;
    private Integer requestSerialnumber;
    
    private BufferedReader br;
    
    private Registry registry;
    
    private final String id;
    private InetAddress raftRegistryIp;
    private Integer raftRegistryPort;
    private boolean isAdmin;
    
    private final Gson gson = new Gson();

    public Client() {
        this("client1");
    }
    
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
        
        this.raftRegistryIp = clientConfiguration.getRaftRegistryIP();
        this.raftRegistryPort = clientConfiguration.getRaftRegistryPort();
        this.isAdmin = clientConfiguration.getIsAdmin();

        // When a client first starts up, it connects to a randomly chosen server
        // If the client’s first choice is not the leader, that server will reject the client’s request and supply
        // information about the most recent leader [done in each method]
        raft = connectToRandomServer();
        if (raft == null) {
            System.err.println("Unable to access the Raft cluster");
            return;
        }

        requestSerialnumber = 0;
    }

    /**
     * Connects to a server available in the registry
     * @return The server's interface
     */
    private RemoteServerInterface connectToRandomServer() {
        String[] availableServers;
        try {
            registry = LocateRegistry.getRegistry(this.raftRegistryIp.getHostAddress(), this.raftRegistryPort);
            availableServers = registry.list();
        } catch (RemoteException e) {
            System.err.println("No registry available at " + this.raftRegistryIp + ":" + this.raftRegistryPort);
            return null;
        }

        String entryPoint = availableServers[ThreadLocalRandom.current().nextInt(0, availableServers.length)];
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
     * Reads a variable from the Raft cluster
     * @param variable The name of the variable
     * @return The variable's value 
     */
    private Integer readFromCluster(String variable) {
        Integer result = null;
        boolean requestComplete = false;
        while (!requestComplete) {
            try {
                result = raft.read(this.id, this.requestSerialnumber, variable);
                this.requestSerialnumber++;
                requestComplete = true;
            } catch (RemoteException e) {
                System.err.println("Connection error, retrying...");
                raft = connectToRandomServer();
            } catch (NotLeaderException e) {
                System.err.println(e + ". Connecting to leader");
                raft = e.getLeader();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    /**
     * Writes a variable on the Raft cluster
     * @param variable The variable to write
     * @param value The value to write
     * @return The number of completed operations
     */
    private Integer writeToCluster(String variable, Integer value) {
        Integer nWritten = null;
        boolean requestComplete = false;
        while (!requestComplete) {
            try {
                nWritten = raft.write(this.id, this.requestSerialnumber, variable, value);
                this.requestSerialnumber++;
                requestComplete = true;
            } catch (RemoteException e) {
                System.err.println("Connection error, retrying...");
                raft = connectToRandomServer();
            } catch (NotLeaderException e) {
                System.err.println(e.getMessage() + ". Connecting to leader");
                raft = e.getLeader();
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
        return nWritten;
    }

    /**
     * Reads a command from terminal
     * @return The split command
     */
    private String[] readCommand() {
        try {
            String line;
            line = br.readLine();
            return line.split(" ");
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }        
    }

    /**
     * Starts the interactive client terminal
     */
    public void startCmd() {
        System.out.println("Raft console" + (isAdmin ?  " - Admin mode" : ""));
        System.out.println("Use 'h' to see available commands");
        
        br = new BufferedReader(new InputStreamReader(System.in));
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
                    System.out.println("Available " + (isAdmin ? " client" : "") + " commands:");
                    System.out.println("\t'r [variable]': Retrieves the variable's value from the cluster");
                    System.out.println("\t'w [variable] [value]': Writes value to the variable");
                    if(isAdmin) {
                        System.out.println("Available admin commands:");
                        System.out.println("\t's [serverName]': starts the server with the given name on this machine (must exist in configuration)");
                        System.out.println("\t'k [serverName]': kills the server with the given name (must be active)");
                        System.out.println("\t'c': enter cluster management mode");
                        System.out.println("Available misc commands:");
                    }
                    System.out.println("\t'h': Opens this menu");
                    System.out.println("\t'q': Stops the client");
                }
                case "r" -> {
                    if (params.length != 2) {
                        System.out.println("Malformed command. Read must be in the form: 'r [variable]'");
                    }
                    else {
                        String var = params[1];
                        System.out.println(readFromCluster(var));
                    }
                }
                case "w" -> {
                    if (params.length != 3) {
                        System.out.println("Malformed command. Write must be in the form: 'r [variable] [IntValue]'");
                    }
                    else {
                        String var = params[1];
                        try {
                            writeToCluster(var, Integer.parseInt(params[2]));
                            System.out.println("Write done");
                        } catch (NumberFormatException e) {
                            System.out.println("Malformed command. Write must be a number");
                        }
                    }
                }
                case "s" -> {
                    if(!isAdmin) {
                        System.out.println("Unrecognized command '" + choice + "'");
                    }
                    else if (params.length != 2) {
                        System.out.println("Malformed command. Start must be in the form: 's [serverName]'");
                    }
                    else {
                        try {
                            ProcessStarter.startServerProcess(params[1], 0, false);
                        } catch (IOException | InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                case "k" -> {
                    if(!isAdmin) {
                        System.out.println("Unrecognized command '" + choice + "'");
                        continue;
                    }
                    try {
                        RemoteServerInterface toKill = (RemoteServerInterface) registry.lookup(params[1]);
                        toKill.stop();
                    } catch (ConnectException ex) {
                        System.err.println("Cannot connect to " + params[1] + ", server is likely not active");
                    }
                    catch (RemoteException | NotBoundException e) {
                        e.printStackTrace();
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
                default -> {
                    System.out.println("Unrecognized command '" + choice + "'");
                }
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
}