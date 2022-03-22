package it.polimi.client.user;

import it.polimi.client.Client;
import it.polimi.exceptions.NotLeaderException;

import java.io.*;
import java.rmi.RemoteException;

public class User extends Client {    
    public User() {
        this("user1");
    }
    
    public User(String userName) {
        super(userName);
    }

    /**
     * Reads a variable from the Raft cluster
     * @param variable The name of the variable
     * @return The variable's value 
     */
    public Integer readFromCluster(String variable) {
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
    public Integer writeToCluster(String variable, Integer value) {
        Integer nWritten = null;
        Integer requestNumber = this.requestSerialnumber;
        this.requestSerialnumber++;
        
        boolean requestComplete = false;
        while (!requestComplete) {
            try {
                nWritten = raft.write(this.id, requestNumber, variable, value);                
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
     * Starts the interactive client terminal
     */
    public void startCmd() {
        System.out.println("Raft console");
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
                    System.out.println("Available commands:");
                    System.out.println("\t'r [variable]': Retrieves the variable's value from the cluster");
                    System.out.println("\t'w [variable] [value]': Writes value to the variable");
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
                case "q" -> {
                    return;
                }
                default -> System.out.println("Unrecognized command '" + choice + "'");
            }
        }
    }
}