package it.polimi.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.polimi.exceptions.NotLeaderException;
import it.polimi.networking.ClientResult;
import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.*;
import it.polimi.server.log.Snapshot;
import it.polimi.server.manager.ClientManager;
import it.polimi.server.manager.ElectionManager;
import it.polimi.server.log.LogEntry;
import it.polimi.server.manager.KeepAliveManager;
import it.polimi.server.state.Candidate;
import it.polimi.server.state.Follower;
import it.polimi.server.state.Leader;
import it.polimi.server.state.State;
import lombok.*;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.nio.file.*;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class Server implements RemoteServerInterface {
    /**
     * Server state
     */
    @Getter
    private State serverState;

    /**
     * Reference to the server's interface
     */
    private RemoteServerInterface selfInterface;

    private static Registry localRegistry;

    /**
     * Reference to the leader
     */
    private static RemoteServerInterface leader;

    private static final Object leaderSync = new Object();

    /**
     * Map of servers in the cluster
     */
    @Getter
    private final Map<String, RemoteServerInterface> cluster;

    private static final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();

    private static Map<Integer, Message.Type> outgoingRequests;
    private static final Object outgoingSync = new Object();
    /**
     * Server id
     */
    @Getter @Setter
    private String id;

    /**
     * Number sent to other servers to couple requests
     * with responses
     */
    private static Integer internalRequestNumber = 0;

    /**
     * Object to synchronize requestNumber
     */
    private static final Object reqNumBlock = new Object();

    @Getter
    private ClientManager clientManager;

    private ElectionManager electionManager;

    private KeepAliveManager keepAliveManager;

    private final Gson gson = new Gson();

    public Server() {
        this("server1");
    }

    /**
     * Class constructor which performs the following actions:
     * <ul>
     *      <li>Init cluster-map and electionThreads-list.</li>
     *      <li>If possible locates RMI registry and bind to it, otherwise create a new one</li>
     * </ul>
     */
    public Server(String serverName) {
        this.cluster = new HashMap<>();
        outgoingRequests = new HashMap<>();
               
        clientManager = new ClientManager(this);

        this.id = serverName;

        ServerConfiguration serverConfiguration;
        try {
            Path storage = Paths.get("./configuration/" + serverName + ".json");
            Type type = new TypeToken<ServerConfiguration>(){}.getType();
            serverConfiguration = gson.fromJson(Files.readString(storage), type);
        } catch (NoSuchFileException e) {
            System.err.println("Cannot find configuration for server '" + serverName + "'. Terminating.");
            return;
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }

        try {
            // Builds the server interface on the given port (or on a random one if null)
            Integer port = serverConfiguration.getPort();
            if(port == null) {
                port = 0;
            }
            this.selfInterface = (RemoteServerInterface) UnicastRemoteObject.exportObject(this, port);

            localRegistry = LocateRegistry.getRegistry();
            try {
                localRegistry.bind(serverName, this.selfInterface);
            } catch (AlreadyBoundException e) {
                localRegistry.rebind(serverName, this.selfInterface);
            } catch (RemoteException e) {
//                e.printStackTrace();
                System.err.println("Creating local RMI registry");
                Integer localPort = serverConfiguration.getRegistryPort();
                if(localPort == null) {
                    localPort = 1099;
                }
                localRegistry = LocateRegistry.createRegistry(localPort);
                localRegistry.bind(serverName, this.selfInterface);
            }

            Registry registry;
            for(ServerConfiguration other: serverConfiguration.getCluster()) {
                InetAddress otherRegistryIP = other.getRegistryIP();
                Integer otherRegistryPort = other.getRegistryPort();

                try {
                    if (otherRegistryIP != null && otherRegistryPort != null) {
                        registry = LocateRegistry.getRegistry(otherRegistryIP.getHostAddress(), otherRegistryPort);
                    } else {
                        // With no given information on the registry the server is seeked locally
                        registry = LocateRegistry.getRegistry();
                    }

                    RemoteServerInterface peer = (RemoteServerInterface) registry.lookup(other.getName());
                    cluster.put(other.getName(), peer);
                    peer.updateCluster(serverName, selfInterface);
                } catch (RemoteException | NotBoundException e) {
                    System.err.println("Server '" + other.getName() + "' at " + otherRegistryIP + ":" + otherRegistryPort + " not available");
                }
            }

            System.err.println("Server '" + serverName + "' ready");

            this.serverState = new Follower(this, serverConfiguration.getVariables());
        } catch (Exception e) {
            System.err.println("Server exception: " + e.toString());
            e.printStackTrace();
        }

        // Start processing messages in the queue
        Message message;
        Result result;
        while(true) {
            try {
                message = messageQueue.take();
                result = null;

                switch (message.getMessageType()) {
                    case AppendEntry -> result = appendEntries((AppendEntries) message);
                    case RequestVote -> result = requestVote((RequestVote) message);
                    case InstallSnapshot -> result = installSnapshot(((InstallSnapshot) message));
                    case Result -> {
                        result = (Result) message;
                        synchronized (outgoingSync) {
                            if (outgoingRequests.containsKey(result.getInternalRequestNumber())) {
                                Message.Type type = outgoingRequests.remove(result.getInternalRequestNumber());
                                this.serverState.processResult(type, result);
                            } else {
                                // If the request was not added yet it is re-enqueued
//                                enqueue(result);
//                                System.err.println("REENQUEUED");
                            }
                        }
                    }
                    case StateTransition -> {
                        if (this.serverState != null) {
                            this.serverState.stopTimers();
                        }
                        if (this.electionManager != null) {
                            this.electionManager.interruptElection();
                        }
                        switch (((StateTransition) message).getState()) {
                            case Follower -> this.updateState(new Follower(this.serverState));
                            case Leader -> this.updateState(new Leader(this.serverState));
                            case Candidate -> this.updateState(new Candidate(this.serverState));
                        }
                    }
                    case StartElection -> {
                        if (electionManager != null) {
                            electionManager.interruptElection();
                        }
                        StartElection startElection = (StartElection) message;
                        electionManager = new ElectionManager(this, cluster,
                                startElection.getTerm(), startElection.getLastLogIndex(), startElection.getLastLogTerm());
                        electionManager.startElection();
                    }
                    case ReadRequest -> {
                        ReadRequest readRequest = (ReadRequest) message;
                        if(this.serverState.getRole() == State.Role.Leader) {
                            // [...] a leader must check whether it has been deposed before processing a read-only 
                            // request [...] by having the leader exchange heartbeat messages with a majority of the 
                            // cluster before responding
                            this.serverState.waitForConfirmation();
                            
                            clientManager.clientRequestComplete(readRequest.getInternalRequestNumber(), readRequest.getClientRequestNumber(), 
                                    this.serverState.getVariable(readRequest.getVariable()));
                        }
                        else {
                            clientManager.clientRequestError(readRequest.getInternalRequestNumber(), readRequest.getClientRequestNumber(), 
                                    ClientResult.Status.NOTLEADER);
                        }
                    }
                    case WriteRequest -> {
                        WriteRequest writeRequest = (WriteRequest) message;
                        if(this.serverState.getRole() == State.Role.Leader) {
                            // If command received from client: append entry to local log,
                            // respond after entry applied to state machine (§5.3)
                            serverState.getLogger().addEntry(serverState.getCurrentTerm(), writeRequest.getVariable(), 
                                    writeRequest.getValue(), message.getInternalRequestNumber(), writeRequest.getClientRequestNumber());
                            serverState.logAdded();
                        }
                        else {
                            clientManager.clientRequestError(writeRequest.getInternalRequestNumber(), writeRequest.getClientRequestNumber(), 
                                    ClientResult.Status.NOTLEADER);
                        }
                    }
                    case UpdateIndex -> this.serverState.setCommitIndex(((UpdateIndex) message).getCommitIndex());
                }

                // Replies to other servers
                if (message.getMessageType() == Message.Type.AppendEntry
                        || message.getMessageType() == Message.Type.RequestVote) {
                    message.getOrigin().reply(result);
                }
            } catch (InterruptedException e) {
                System.err.println(Thread.currentThread().getId() + " - Thread interrupted, terminating");
                if(this.keepAliveManager != null) {
                    this.keepAliveManager.stopKeepAlive();
                }
                if(this.electionManager != null) {
                    this.electionManager.interruptElection();
                }
                return;
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public RemoteServerInterface getLeader() {
        synchronized (leaderSync) {
            if(leader == null) {
                try {
                    leaderSync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
        return leader;
    }

    public void setLeader(RemoteServerInterface leader) {
        synchronized (leaderSync) {
            Server.leader = leader;
            // Wakes threads waiting to do a getLeader()
            leaderSync.notifyAll();
        }
    }

    /**
     * {@inheritDoc}
     */
//    public int addToCluster(RemoteServerInterface follower) throws RemoteException {
//        int id;
//        do {
//            id = ThreadLocalRandom.current().nextInt(0, 10000);
//        } while(cluster.containsKey(id));
//
//        System.err.println("Adding to cluster");
//        System.err.println(this);
//
//        System.err.println(Thread.currentThread().getId() + " Add cluster");
//        Thread t = new Thread(()->keepAlive(follower));
//        t.start();
//
//        // Adds self to follower
//        follower.addToCluster(this.id, this);
//
//        for(Map.Entry<String, RemoteServerInterface> entry: cluster.entrySet()) {
//            // Adds to others
//            entry.getValue().addToCluster(id, follower);
//
//            //Adds others to follower
//            follower.addToCluster(entry.getKey(), entry.getValue());
//        }
//
//        // Adds to self
//        addToCluster(id, follower);
//
//        return id;
//    }

    /**
     * {@inheritDoc}
     */
    public void addToCluster(String id, RemoteServerInterface server) {
        cluster.put(id, server); // todo synchronize on cluster?
    }

    /**
     * Puts a message in the message queue
     * @param message The message
     */
    public void enqueue(Message message) {
        synchronized (messageQueue) {
            try {
                messageQueue.put(message);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void addRequest(Integer receipt, Message.Type messageType) {
        synchronized (outgoingSync) {
            outgoingRequests.put(receipt, messageType);
        }
    }

    /**
     * {@inheritDoc}
     */
    public int appendEntries(RemoteServerInterface origin, int term, String leaderId, Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> newEntries, Integer leaderCommit) throws RemoteException {
        int currentRequest = nextRequestNumber();
        
        enqueue(new AppendEntries(currentRequest, origin, term, leaderId, prevLogIndex, prevLogTerm, newEntries, leaderCommit));

        return currentRequest;
    }

    public Result appendEntries(AppendEntries message) {
        // Set leader
        setLeader(cluster.get(message.getLeaderId()));

        // Stops [If election timeout elapses without receiving AppendEntries RPC from current leader or granting
        // vote to candidate: convert to candidate]
        this.serverState.receivedAppend(message.getTerm());

        Integer currentTerm = this.serverState.getCurrentTerm();

        // 1. Reply false if term < currentTerm (§5.1)
        if(currentTerm != null && message.getTerm() < currentTerm) {
            System.out.println("AppendEntries ignored: term " + message.getTerm() + " < " + currentTerm);
            return new Result(message.getInternalRequestNumber(), currentTerm, false);
        }

        // 2. Reply false if log does not contain an entry at prevLogIndex
        // whose term matches prevLogTerm (§5.3)
        if(message.getPrevLogTerm() != null && !message.getPrevLogTerm().equals(this.serverState.getLogger().termAtPosition(message.getPrevLogIndex()))) {
            System.out.println("AppendEntries ignored: no log entry at prevLogIndex = " + message.getPrevLogIndex());
            return new Result(message.getInternalRequestNumber(), currentTerm, false);
        }

        if(message.getNewEntries() != null) {
            // 3. If an existing entry conflicts with a new one (same index but different terms),
            // delete the existing entry and all that follow it (§5.3)
            for (Map.Entry<Integer, LogEntry> entry : message.getNewEntries().entrySet()) {
                if (this.serverState.getLogger().containConflict(entry.getKey(), entry.getValue().getTerm())) {
                    this.serverState.getLogger().deleteFrom(entry.getKey());
                }
            }

            // 4. Append any new entries not already in the log
            this.serverState.getLogger().appendNewEntries(message.getNewEntries());
        }

        Integer commitIndex = this.serverState.getCommitIndex();
        // 5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        if(message.getLeaderCommit() != null && (commitIndex == null || message.getLeaderCommit() > commitIndex)) {
            int newIndex;
            try {
                newIndex = Math.min(message.getLeaderCommit(), message.getNewEntries().lastKey());
            } catch(NoSuchElementException | NullPointerException e) {
                newIndex = message.getLeaderCommit();
            }
            this.serverState.setCommitIndex(newIndex);
        }

        return new Result(message.getInternalRequestNumber(), currentTerm, true);
    }

    public int requestVote(RemoteServerInterface origin, int term, String candidateId, Integer lastLogIndex, Integer lastLogTerm) throws RemoteException {
        int currentRequest = nextRequestNumber();
        
        enqueue(new RequestVote(currentRequest, origin, term, candidateId, lastLogIndex, lastLogTerm));

        return currentRequest;
    }

    /**
     * {@inheritDoc}
     */
    public Result requestVote(RequestVote message) {
        int term = message.getTerm();
        String candidateId = message.getCandidateId();
        Integer lastLogIndex = message.getLastLogIndex();

        this.serverState.convertOnNextTerm(term);

        int currentTerm = serverState.getCurrentTerm();

        // [...] removed servers (those not in Cnew) can disrupt the cluster. [...]
        // To prevent this problem, servers disregard RequestVote RPCs when they believe a current leader exists.
        // Specifically, if a server receives a RequestVote RPC within the minimum election timeout of hearing
        // from a current leader, it does not update its term or grant its vote.
//        if(this.serverState.getRole() == State.Role.Leader
//                || !State.getElapsedMinTimeout()) {
//            return new Result(message.getRequestNumber(), currentTerm, false);
//        }

        // 1. Reply false if term < currentTerm (§5.1)
        if(term < currentTerm) {
            return new Result(message.getInternalRequestNumber(), currentTerm, false);
        }

        // 2. If votedFor is null or candidateId, and candidate’s log is at
        //    least as up-to-date as receiver’s log, grant vote (§5.2, §5.4)
        String votedFor = serverState.getVotedFor();
        if((votedFor == null || votedFor.equals(candidateId))
                && ((lastLogIndex == null && serverState.getLastLogIndex() == null)
                    || (lastLogIndex != null
                        && (serverState.getLastLogIndex() == null || lastLogIndex >= serverState.getLastLogIndex())))) {
            serverState.setVotedFor(candidateId);

            // Stops [If election timeout elapses without receiving AppendEntries RPC from current leader or granting
            // vote to candidate: convert to candidate]
            this.serverState.receivedMsg(term);

            System.out.println(Thread.currentThread().getId() + " [Term " + currentTerm + "] Voted for " + candidateId);
            return new Result(message.getInternalRequestNumber(), currentTerm, true);
        }

        return new Result(message.getInternalRequestNumber(), currentTerm, false);
    }
    
    public Integer nextRequestNumber() {
        Integer currentRequest;
        synchronized (reqNumBlock) {
            currentRequest = internalRequestNumber;
            internalRequestNumber++;
        }
        return currentRequest;
    }

    @Override
    public int installSnapshot(RemoteServerInterface origin, int term, String leaderId, Integer lastIncludedIndex, Integer lastIncludedTerm, int offset, byte[] data, boolean done) throws RemoteException {
        int currentRequest = nextRequestNumber();

        enqueue(new InstallSnapshot(currentRequest, origin, term, leaderId, lastIncludedIndex, lastIncludedTerm, offset, data, done));

        return currentRequest;
    }

    public Result installSnapshot(InstallSnapshot message) {
        // 1. Reply immediately if term < currentTerm
        if(message.getTerm() < this.serverState.getCurrentTerm()) {
            return new Result(message.getInternalRequestNumber(), this.serverState.getCurrentTerm(), false);
        }
        
        // 2. Create new snapshot file if first chunk (offset is 0)
        Path snapshotPath = Paths.get(this.serverState.getLogger().getStorage() + "_" + message.getLastIncludedIndex() + ".temp");
        RandomAccessFile snapshotTempFile = null;
        if(message.getOffset() == 0) {
            try {
                Files.deleteIfExists(snapshotPath);
                snapshotTempFile = new RandomAccessFile(Files.createFile(snapshotPath).toFile(), "rw");
            } catch (IOException e) {
                e.printStackTrace();
                return new Result(message.getInternalRequestNumber(), this.serverState.getCurrentTerm(), false);
            }
        }
        else {
            try {
                snapshotTempFile = new RandomAccessFile(snapshotPath.toString(), "rw");
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                return new Result(message.getInternalRequestNumber(), this.serverState.getCurrentTerm(), false);
            }
        }
        
        // 3. Write data into snapshot file at given offset
        try {
            // If the file is not long enough it is extended
//            if(snapshotTempFile.length() < message.getOffset()) {
//                snapshotTempFile.setLength(message.getOffset());
//            }
            snapshotTempFile.seek((long) message.getOffset() * Snapshot.CHUNK_DIMENSION);
            snapshotTempFile.write(message.getData());
            snapshotTempFile.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        

        // 4. Reply and wait for more data chunks if done is false
        if(!message.isDone()) {
            return new Result(message.getInternalRequestNumber(), this.serverState.getCurrentTerm(), true);
        }
        
        // 5. Save snapshot file, discard any existing or partial snapshot with a smaller index
        try {
            Files.deleteIfExists(this.serverState.getLogger().getStorage());
            Files.copy(snapshotPath, this.serverState.getLogger().getStorage());
            Files.deleteIfExists(snapshotPath);
        } catch (IOException e) {
            e.printStackTrace();
        }        
        
        // 6. If existing log entry has same index and term as snapshot’s last included entry, retain log entries following it and reply
        try {
            LogEntry lastSnapshotEntry = this.getServerState().getLogger().getEntry(message.getLastIncludedIndex());
            if (lastSnapshotEntry != null && lastSnapshotEntry.getTerm() == message.getTerm()) {
                this.getServerState().getLogger().clearUpToIndex(message.getLastIncludedIndex(), false);
                return new Result(message.getInternalRequestNumber(), this.serverState.getCurrentTerm(), true);
            }
        } catch(NoSuchElementException e) {
            // The last element did not exist, so continue
        } catch(Exception e) {
            e.printStackTrace();
        }
        
        // 7. Discard the entire log
        this.getServerState().getLogger().clearEntries();
        
        // 8. Reset state machine using snapshot contents (and load snapshot’s cluster configuration)
        this.getServerState().restoreVars();
        
        System.out.println("InstallSnapshot: updated variables = " + getServerState().getVariables());
        
        return new Result(message.getInternalRequestNumber(), this.serverState.getCurrentTerm(), true);
    }


    @Override
    public void reply(Result result) throws RemoteException {
        enqueue(result);
    }

    public void startKeepAlive() {
        this.keepAliveManager = new KeepAliveManager(this, cluster);
        this.keepAliveManager.startKeepAlive();
    }

    @Override
    public synchronized void updateCluster(String serverName, RemoteServerInterface serverInterface) {
        this.cluster.put(serverName, serverInterface);
        if(this.serverState != null && this.serverState.getRole() == State.Role.Leader) {
            this.keepAliveManager.startKeepAlive(serverName, serverInterface); // todo might not have correct cluster
            this.serverState.startReplication(serverName, serverInterface);
        }
    }

    @Override
    public Integer read(String clientId, Integer clientRequestNumber, String variable) throws RemoteException, NotLeaderException {
        return clientManager.read(clientId, clientRequestNumber, variable);
    }

    @Override
    public Integer write(String clientId, Integer clientRequestNumber, String variable, Integer value) throws RemoteException, NotLeaderException {
        return clientManager.write(clientId, clientRequestNumber, variable, value);
    }

    /**
     * Updates the server state
     * @param next The next state
     */
    public synchronized void updateState(State next) {
        this.serverState = next;
    }

    /**
     * Get the size of the server cluster (considering itself)
     * @return The cluster size
     */
    public int getClusterSize() {
        return cluster.size() + 1; // +1 to consider self
    }

    public Set<String> getServersInCluster() {
        return cluster.keySet();
    }

    @Override
    public String toString() {
        return "{\n" +
                "   'serverState':" + serverState.toString() +
                "',\n   'cluster':'" + cluster.toString() +
                "',\n   'id':'" + id +
                "'\n}";
    }
}
