package it.polimi.server;


import it.polimi.utilities.OutputInterceptor;

/**
 * Launcher class for the server
 */
public class AppServer {
    /**
     * Main for the server application
     */
    public static void main(String[] args) {
        // args[0] -> name of the server and of the configuration
        // args[1] -> tabs to shift output
        
        Server server;
        switch (args.length) {
            case 2:
                System.setOut(new OutputInterceptor(System.out, false, Integer.parseInt(args[1])));
                System.setErr(new OutputInterceptor(System.err, true, Integer.parseInt(args[1])));
            case 1:
                server = new Server(args[0]);
                break;
            default:
                server = new Server();
        }
        
        server.start();
    }
}
