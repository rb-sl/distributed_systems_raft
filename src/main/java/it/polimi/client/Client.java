package it.polimi.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.polimi.exceptions.NotLeaderException;
import it.polimi.networking.RemoteServerInterface;
import it.polimi.server.ServerConfiguration;

import java.io.*;
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

public class Client implements Remote, Serializable {
    private final static String TIMEOUT = "10000"; // 10 seconds
    private RemoteServerInterface raft;
    private Integer requestSerialnumber;
    
    private String id;
    private InetAddress raftRegistryIp;
    private Integer raftRegistryPort;
    
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
        
        raftRegistryIp = clientConfiguration.getRaftRegistryIP();
        raftRegistryPort = clientConfiguration.getRaftRegistryPort();        

        // When a client first starts up, it connects to a randomly chosen server
        raft = connectToRandomServer();
        if (raft == null) {
            return;
        }

        requestSerialnumber = 0;

        // If the client’s first choice is not the leader, that server will reject the client’s request and supply
        // information about the most recent leader
//        Integer response = readFromCluster("x");

//        System.out.println("response: " + response);
//
//        writeToCluster("x", response + 1);
        cmd();
    }

    private RemoteServerInterface connectToRandomServer() {
        Registry registry;
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
                e.printStackTrace();
                raft = connectToRandomServer();
            } catch (NotLeaderException e) {
                System.err.println(e + ". Connecting to leader");
                raft = e.getLeader();
            }
        }
        return result;
    }

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
//                e.printStackTrace();
                raft = connectToRandomServer();
            } catch (NotLeaderException e) {
                System.err.println(e.getMessage() + ". Connecting to leader");
                raft = e.getLeader();
            }
        }
        return nWritten;
    }

    private void cmd() {
        while (true) {
            BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
            String line;
            try {
                line = br.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                continue;
            }
            String[] params = line.split(" ");
            String choice;
            try {
                 choice = params[0];
            } catch(ArrayIndexOutOfBoundsException e) {
                choice = "";
            }
            switch (choice) {
                case "r" -> {
                    if (params.length != 2) {
                        System.out.println("Malformed command. Read must be in the form: \"r variable\"");
                    }
                    else {
                        String var = params[1];
                        System.out.println(readFromCluster(var));
                    }
                }
                case "w" -> {
                    if (params.length != 3) {
                        System.out.println("Malformed command. Write must be in the form: \"r variable IntValue\"");
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
                case "q" -> {
                    return;
                }
                default -> {
                    System.out.println("Malformed command. Command must be in the form: \"type variable [WriteIntValue]\"");
                }
            }
        }
    }
}