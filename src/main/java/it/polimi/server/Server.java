package it.polimi.server;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.server.state.Follower;
import it.polimi.server.state.State;
import lombok.Getter;
import lombok.Setter;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class Server implements RemoteServerInterface {
    /**
     * Server state
     */
    private State serverState;

    /**
     * Reference to the server's interface
     */
    private RemoteServerInterface selfInterface;

    /**
     * Map of servers in the cluster
     */
    private Map<Integer, RemoteServerInterface> cluster;

    /**
     * Server id
     */
    @Getter @Setter
    private int id;

    public Server() {
        this.serverState = new Follower();
        this.cluster = new HashMap<>();

        try {
            this.selfInterface = (RemoteServerInterface) UnicastRemoteObject.exportObject(this, 0);

            Registry registry;
            try {
                registry = LocateRegistry.getRegistry();
                String accessPoint = registry.list()[0];

                RemoteServerInterface ap = (RemoteServerInterface) registry.lookup(accessPoint);
                RemoteServerInterface leader = ap.getLeader();
                this.id = leader.addToCluster(this.selfInterface);
            } catch (RemoteException e) {
                registry = LocateRegistry.createRegistry(1099);
                this.id = ThreadLocalRandom.current().nextInt(0, 10000);
            }

            registry.bind("Server" + this.id, this.selfInterface);

            System.out.println(Arrays.toString(registry.list()));

            System.err.println("Server ready");
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }
    }

    public RemoteServerInterface getLeader() {
        return this.selfInterface;
    }

    public int addToCluster(RemoteServerInterface follower) {
        return ThreadLocalRandom.current().nextInt(0, 10000);
    }
}
