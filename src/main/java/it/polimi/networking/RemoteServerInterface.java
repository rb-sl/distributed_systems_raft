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
        Integer read(String variable) throws RemoteException;
        void write(String variable, Integer value) throws RemoteException;
}
