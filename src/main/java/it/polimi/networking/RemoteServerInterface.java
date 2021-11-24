package it.polimi.networking;

import it.polimi.networking.messages.Result;
import it.polimi.server.log.LogEntry;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.SortedMap;

public interface RemoteServerInterface extends Remote {
        /**
         * Returns the leader's interface
         * @return The leader's interface
         * @throws RemoteException Una cosa di RMI da mettere sempre, se no si incazza
         */
        RemoteServerInterface getLeader() throws RemoteException;

        /**
         * Add the follower to the cluster
         * @param follower The follower
         * @return The given ID
         * @throws RemoteException Una cosa di RMI da mettere sempre, se no si incazza
         */
        int addToCluster(RemoteServerInterface follower) throws RemoteException;

        /**
         *
         * @param term Leader's term
         * @param leaderId So follower can redirect clients
         * @param prevLogIndex Index of log entry immediately preceding new ones
         * @param prevLogTerm Term of prevLogIndex entry
         * @param entries Log entries to store (empty for heartbeat; may send more than one for efficiency)
         * @param leaderCommit leader’s commitIndex
         * @return The Result object, with term: currentTerm, for leader to update itself, success: true if follower contained entry matching prevLogIndex and prevLogTerm
         */
        Result appendEntries(int term, Integer leaderId, Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> entries, Integer leaderCommit) throws RemoteException;
}
