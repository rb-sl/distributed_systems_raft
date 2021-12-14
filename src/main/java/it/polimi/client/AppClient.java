package it.polimi.client;

import it.polimi.networking.RemoteServerInterface;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Class used to launch the client
 */
public class AppClient {
    /**
       Main for the client application
     */
    public static void main(String[] args) {
        String host = (args.length < 1) ? null : args[0];
        try {
            Registry registry = LocateRegistry.getRegistry(host);
            RemoteServerInterface stub = (RemoteServerInterface) registry.lookup("server3");
            Integer response = stub.read("x");
            System.out.println("response: " + response);
            stub.write("x", response + 1);
        } catch (Exception e) {
            System.err.println("Client exception: " + e.toString());
            e.printStackTrace();
        }
    }
}
