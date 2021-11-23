package it.polimi.networking;

import it.polimi.server.Server;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface RemoteServerInterface extends Remote {
        RemoteServerInterface getLeader() throws RemoteException;
        int addToCluster(RemoteServerInterface follower) throws RemoteException;
}
