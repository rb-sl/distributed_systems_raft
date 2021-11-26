package it.polimi.server;


import java.rmi.server.UnicastRemoteObject;

/**
 * Launcher class for the server
 */
public class AppServer {
    private static Server server;
    /**
     * Main for the server application
     */
    public static void main(String[] args) {
        // TODO argument for storage file path
        server = new Server();
    }
}
