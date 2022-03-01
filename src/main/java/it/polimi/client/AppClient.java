package it.polimi.client;

/**
 * Class used to launch the client
 */
public class AppClient {
    /**
       Main for the client application
     */
    public static void main(String[] args) {
        Client client;
        
        // args[0] -> clientName
        if(args.length == 1) {
            client = new Client(args[0]);
        }
        else {
            client = new Client();
        }
        
        client.startCmd();
    }
}
