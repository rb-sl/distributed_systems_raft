package it.polimi.networking;

import it.polimi.exceptions.NotLeaderException;
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
         *
         * @param id
         * @param follower The follower
         * @return The given ID
         * @throws RemoteException Una cosa di RMI da mettere sempre, se no si incazza
         */
//        int addToCluster(String id, RemoteServerInterface follower) throws RemoteException;

//        void addToCluster(int id, RemoteServerInterface follower) throws RemoteException;

        /**
         *
         * @param term Leader's term
         * @param leaderId So follower can redirect clients
         * @param prevLogIndex Index of log entry immediately preceding new ones
         * @param prevLogTerm Term of prevLogIndex entry
         * @param entries Log entries to store (empty for heartbeat; may send more than one for efficiency)
         * @param leaderCommit leader’s commitIndex
         * @return Request number
         */
        int appendEntries(RemoteServerInterface origin, int term, String leaderId, Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> entries, Integer leaderCommit) throws RemoteException;

        /**
         *
         * @param term Candidate’s term
         * @param candidateId Candidate requesting vote
         * @param lastLogIndex Index of candidate’s last log entry (§5.4)
         * @param lastLogTerm term of candidate’s last log entry (§5.4)
         * @return Request number
         * @throws RemoteException Una cosa di RMI da mettere sempre, se no si incazza
         */
        int requestVote(RemoteServerInterface origin, int term, String candidateId, Integer lastLogIndex, Integer lastLogTerm) throws RemoteException;

        void reply(Result result) throws RemoteException;

        void updateCluster(String serverName, RemoteServerInterface serverInterface) throws RemoteException;

        // Methods called by clients
        /**
         * Asks the leader of the cluster a variable's value
         * @param clientRequestNumber The number assigned to the request by the client
         * @param variable The variable's name
         * @return The variable value
         * @throws RemoteException Roba di RMI
         * @throws NotLeaderException When the queried server is not the leader
         */
        Integer read(String clientId, Integer clientRequestNumber, String variable) throws RemoteException, NotLeaderException;

        /**
         * Asks the leader of the cluster to write a variable's value.
         * @param clientRequestNumber The number assigned to the request by the client
         * @param variable The variable's name
         * @param value The variable's value
         * @return The number of written elements
         * @throws RemoteException Roba di RMI
         * @throws NotLeaderException When the queried server is not the leader
         */
        Integer write(String clientId, Integer clientRequestNumber, String variable, Integer value) throws RemoteException, NotLeaderException;
}
