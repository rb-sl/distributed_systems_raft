package it.polimi.server;


import java.rmi.server.UnicastRemoteObject;

/**
 * Launcher class for the server
 */
public class AppServer {
    private  Server server;
    /**
     * Main for the server application
     */
    public static void main(String[] args) {
    // args[0] -> name of the server and of the configuration
        if(args.length == 1) {
             new Server(args[0]);
        }
        else {
            new Server();
        }
    }
}
