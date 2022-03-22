package it.polimi.networking;

import it.polimi.exceptions.NotLeaderException;
import it.polimi.networking.messages.Result;
import it.polimi.server.ServerConfiguration;
import it.polimi.server.log.LogEntry;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.SortedMap;

public interface RemoteServerInterface extends Remote {
        /**
         * Returns the leader's interface
         * @return The leader's interface
         * @throws RemoteException For RMI
         */
        RemoteServerInterface getLeader() throws RemoteException;
        
        /**
         * Invoked by leader to replicate log entries (§5.3); also used as heartbeat (§5.2).
         * @param term Leader's term
         * @param leaderId So follower can redirect clients
         * @param prevLogIndex Index of log entry immediately preceding new ones
         * @param prevLogTerm Term of prevLogIndex entry
         * @param entries Log entries to store (empty for heartbeat; may send more than one for efficiency)
         * @param leaderCommit leader’s commitIndex
         */
        void appendEntries(RemoteServerInterface origin, Integer requestNumber, int term, String leaderId, 
                           Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> entries, 
                           Integer leaderCommit) throws RemoteException;

        /**
         * Invoked by candidates to gather votes (§5.2).
         * @param term Candidate’s term
         * @param candidateId Candidate requesting vote
         * @param lastLogIndex Index of candidate’s last log entry (§5.4)
         * @param lastLogTerm term of candidate’s last log entry (§5.4)
         * @throws RemoteException For RMI
         */
        void requestVote(RemoteServerInterface origin, Integer requestNumber, int term, String candidateId,
                         Integer lastLogIndex, Integer lastLogTerm) throws RemoteException;

        /**
         * Invoked by leader to send chunks of a snapshot to a follower. Leaders always send chunks in order.
         * @param origin The leader's interface
         * @param term Leader’s term
         * @param leaderId So follower can redirect clients
         * @param lastIncludedIndex The snapshot replaces all entries up through and including this index
         * @param lastIncludedTerm Term of lastIncludedIndex
         * @param offset Byte offset where chunk is positioned in the snapshot file
         * @param data Raw bytes of the snapshot chunk, starting at offset
         * @param done true if this is the last chunk
         * @throws RemoteException For RMI
         */
        void installSnapshot(RemoteServerInterface origin, Integer requestNumber,
                            int term, String leaderId, Integer lastIncludedIndex, Integer lastIncludedTerm,
                            int offset, byte[] data, boolean done) throws RemoteException;

        /**
         * Receive remote execution result
         * @param result The result
         * @throws RemoteException RMI exception
         */
        void reply(Result result) throws RemoteException;

        /**
         * Starts keepalive and replication on a new available server
         * @param serverName The new server name
         * @param serverInterface RemoteServerInterface object
         * @throws RemoteException RMI exception
         */
        void notifyAvailability(String serverName, RemoteServerInterface serverInterface) throws RemoteException;

        // Methods called by clients
        /**
         * Asks the leader of the cluster a variable's value
         * @param clientRequestNumber The number assigned to the request by the client
         * @param variable The variable's name
         * @return The variable value
         * @throws RemoteException For RMI
         * @throws NotLeaderException When the queried server is not the leader
         */
        Integer read(String clientId, Integer clientRequestNumber, String variable) throws RemoteException, NotLeaderException;

        /**
         * Asks the leader of the cluster to write a variable's value.
         * @param clientRequestNumber The number assigned to the request by the client
         * @param variable The variable's name
         * @param value The variable's value
         * @return The number of written elements
         * @throws RemoteException For RMI
         * @throws NotLeaderException When the queried server is not the leader
         */
        Integer write(String clientId, Integer clientRequestNumber, String variable, Integer value) throws RemoteException, NotLeaderException;
        
        void changeConfiguration(String clientId, Integer clientRequestNumber, Map<String, ServerConfiguration> newConfigurations) throws RemoteException, NotLeaderException;

        /**
         * Stops the server execution
         * @throws RemoteException For RMI
         */
        void stop() throws RemoteException;
}
