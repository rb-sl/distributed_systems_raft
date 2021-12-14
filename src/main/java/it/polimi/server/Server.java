package it.polimi.server;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.*;
import it.polimi.server.leaderElection.ElectionManager;
import it.polimi.server.log.LogEntry;
import it.polimi.server.state.Candidate;
import it.polimi.server.state.Follower;
import it.polimi.server.state.Leader;
import it.polimi.server.state.State;
import lombok.*;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
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
    @Getter(AccessLevel.PROTECTED)
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

    private static BlockingQueue<Message> messageQueue;

    private static Map<Integer, Message.Type> outgoingRequests;

    /**
     * Server id
     */
    @Getter @Setter
    private String id;

    /**
     * Number sent to other servers to couple requests
     * with responses
     */
    private static Integer requestNumber = 0;

    /**
     * Object to synchronize requestNumber
     */
    private static final Object reqNumBlock = new Object();

    private final Map<Integer, Integer> clientResponse = new HashMap<>();
    private final Object clientResponseSync = new Object();

    private ElectionManager electionManager;

    private keepAliveManager keepAliveManager;

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
        messageQueue = new LinkedBlockingQueue<>();
        outgoingRequests = new HashMap<>();

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
                    case Result -> {
                        result = (Result) message;
                        if(outgoingRequests.containsKey(result.getRequestNumber())) {
                            Message.Type type = outgoingRequests.remove(result.getRequestNumber());
                            this.serverState.processResult(type, result);
                        }
                        else {
                            // If the request was not added yet it is re-enqueued
                            messageQueue.put(result);
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
                        clientRequestComplete(message.getRequestNumber(), this.serverState.getVariable(readRequest.getVariable()));
                    }
                    case WriteRequest -> {
                        WriteRequest writeRequest = (WriteRequest) message;
                        if(this.serverState.getRole() == State.Role.Leader) {
                            // If command received from client: append entry to local log,
                            // respond after entry applied to state machine (§5.3)
                            serverState.getLogger().addEntry(serverState.getCurrentTerm(), writeRequest.getVariable(), writeRequest.getValue(), message.getRequestNumber());
                            serverState.logAdded();
                        }
                        else {
                            leader.write(writeRequest.getVariable(), writeRequest.getValue());
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
    public synchronized void enqueue(Message message) {
        try {
            messageQueue.put(message);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public synchronized void addRequest(Integer receipt, Message.Type messageType) {
        outgoingRequests.put(receipt, messageType);
    }

    /**
     * {@inheritDoc}
     */
    public int appendEntries(RemoteServerInterface origin, int term, String leaderId, Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> newEntries, Integer leaderCommit) throws RemoteException {
        int currentRequest = -1;

        synchronized (reqNumBlock) {
            currentRequest = requestNumber;
            requestNumber++;
        }
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
            return new Result(message.getRequestNumber(), currentTerm, false);
        }

        // 2. Reply false if log does not contain an entry at prevLogIndex
        // whose term matches prevLogTerm (§5.3)
        if(message.getPrevLogTerm() != null && !message.getPrevLogTerm().equals(this.serverState.getLogger().termAtPosition(message.getPrevLogIndex()))) {
            System.out.println("AppendEntries ignored: no log entry at prevLogIndex = " + message.getPrevLogIndex());
            return new Result(message.getRequestNumber(), currentTerm, false);
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

        return new Result(message.getRequestNumber(), currentTerm, true);
    }

    public int requestVote(RemoteServerInterface origin, int term, String candidateId, Integer lastLogIndex, Integer lastLogTerm) throws RemoteException {
        int currentRequest = -1;

        synchronized (reqNumBlock) {
            currentRequest = requestNumber;
            requestNumber++;
        }
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
            return new Result(message.getRequestNumber(), currentTerm, false);
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
            return new Result(message.getRequestNumber(), currentTerm, true);
        }

        return new Result(message.getRequestNumber(), currentTerm, false);
    }

    @Override
    public void reply(Result result) throws RemoteException {
        enqueue(result);
    }

    public void startKeepAlive() {
        this.keepAliveManager = new keepAliveManager(this, cluster);
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

    private Integer waitResponse(Integer currentRequest) {
        synchronized (clientResponseSync) {
            while(!clientResponse.containsKey(currentRequest)) {
                try {
                    clientResponseSync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            return clientResponse.remove(currentRequest);
        }
    }

    @Override
    public Integer read(String variable) throws RemoteException {
        Integer currentRequest;
        synchronized (reqNumBlock) {
            currentRequest = requestNumber;
            requestNumber++;
        }
        enqueue(new ReadRequest(currentRequest, variable));

        return waitResponse(currentRequest);
    }

    @Override
    public Integer write(String variable, Integer value) throws RemoteException {
        Integer currentRequest;
        synchronized (reqNumBlock) {
            currentRequest = requestNumber;
            requestNumber++;
        }
        enqueue(new WriteRequest(currentRequest, variable, value));

        // An answer is provided only after that the request has been applied to the
        // state machine
        return waitResponse(currentRequest);
    }

    public void clientRequestComplete(Integer requestNumber, Integer result) {
        synchronized (clientResponseSync) {
            clientResponse.put(requestNumber, result);
            clientResponseSync.notifyAll();
        }
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
