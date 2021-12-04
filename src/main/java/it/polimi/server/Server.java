package it.polimi.server;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.*;
import it.polimi.server.log.LogEntry;
import it.polimi.server.state.Candidate;
import it.polimi.server.state.Follower;
import it.polimi.server.state.Leader;
import it.polimi.server.state.State;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadLocalRandom;

public class Server implements RemoteServerInterface {
    /**
     * Server state
     */
    @Getter(AccessLevel.PROTECTED)
    private State serverState;

    /**
     * Reference to the server's interface
     */
    private final RemoteServerInterface selfInterface;

    /**
     * Reference to the leader
     */
    @Getter @Setter
    private RemoteServerInterface leader;

    /**
     * Map of servers in the cluster
     */
    private final Map<Integer, RemoteServerInterface> cluster;

    private static BlockingQueue<Message> messageQueue;

    private static Map<Integer, Message.Type> outgoingRequests;

    /**
     * Server id
     */
    @Getter @Setter
    private int id;

    /**
     * Number sent to other servers to couple requests
     * with responses
     */
    private static Integer requestNumber = 0;

    /**
     * Object to synchronize requestNumber
     */
    private static final Object reqNumBlock = new Object();

    /**
     * List of election threads.
     * Used to ask for votes
     */
    List<Thread> electionThreads;

    /**
     * Class constructor which performs the following actions:
     * <ul>
     *      <li>Init cluster-map and electionThreads-list.</li>
     *      <li>If possible locates RMI registry and bind to it, otherwise create a new one</li>
     * </ul>
     */
    public Server() {
        this.cluster = new HashMap<>();
        this.electionThreads = new ArrayList<>();
        messageQueue = new LinkedBlockingQueue<>();
        outgoingRequests = new HashMap<>();

        try {
            this.selfInterface = (RemoteServerInterface) UnicastRemoteObject.exportObject(this, 0);

            Registry registry;
            try {
                registry = LocateRegistry.getRegistry();
                String accessPoint = registry.list()[0];

                RemoteServerInterface ap = (RemoteServerInterface) registry.lookup(accessPoint);
                this.leader = ap.getLeader();
                this.id = this.leader.addToCluster(this.selfInterface);
            } catch (RemoteException e) {
                System.err.println("Creating RMI registry");
                registry = LocateRegistry.createRegistry(1099);
                this.id = ThreadLocalRandom.current().nextInt(0, 10000);
            }

            registry.bind("Server" + this.id, this.selfInterface);

            System.out.println(Arrays.toString(registry.list()));

            System.err.println("Server " + this.id + " ready");

            this.serverState = new Follower(this);
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
                        Message.Type type = outgoingRequests.remove(result.getRequestNumber());
                        this.serverState.processResult(type, result);
                    }
                    case StateTransition -> {
                        switch(((StateTransition) message).getState()) {
                            case Follower -> this.updateState(new Follower(this.serverState));
                            case Leader -> this.updateState(new Leader(this.serverState));
                            case Candidate -> this.updateState(new Candidate(this.serverState));
                        }
                    }
                }

                if(message.getMessageType() == Message.Type.AppendEntry
                        || message.getMessageType() == Message.Type.RequestVote) {
                    message.getOrigin().reply(result);
                }
            } catch (InterruptedException | RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public int addToCluster(RemoteServerInterface follower) throws RemoteException {
        int id;
        do {
            id = ThreadLocalRandom.current().nextInt(0, 10000);
        } while(cluster.containsKey(id));

        while(leader == null) {} // todo change

        System.err.println("Adding to cluster");
        System.err.println(this);

        System.err.println(Thread.currentThread().getId() + " Add cluster");
        Thread t = new Thread(()->keepAlive(follower));
        t.start();

        // Adds self to follower
        follower.addToCluster(id, this); // todo ok or need to return?

        // Adds to all others
        for(Map.Entry<Integer, RemoteServerInterface> entry: cluster.entrySet()) {
            entry.getValue().addToCluster(id, follower);
        }

        // Adds to self
        addToCluster(id, follower);

        return id;
    }

    /**
     * {@inheritDoc}
     */
    public void addToCluster(int id, RemoteServerInterface server) {
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

    /**
     * {@inheritDoc}
     */
    public int appendEntries(RemoteServerInterface origin, int term, Integer leaderId, Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> newEntries, Integer leaderCommit) throws RemoteException {
        int currentRequest = -1;

        synchronized (reqNumBlock) {
            currentRequest = requestNumber;
            requestNumber++;
        }
        enqueue(new AppendEntries(currentRequest, origin, term, leaderId, prevLogIndex, prevLogTerm, newEntries, leaderCommit));

        return currentRequest;
    }

    public Result appendEntries(AppendEntries message) {
        int currentTerm = this.serverState.getCurrentTerm();

        // Set leader
        leader = cluster.get(message.getLeaderId());

        // Stops [If election timeout elapses without receiving AppendEntries RPC from current leader or granting
        // vote to candidate: convert to candidate]
        this.serverState.receivedAppend(message.getTerm());

        // 1. Reply false if term < currentTerm (§5.1)
        if(message.getTerm() < currentTerm) {
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

        // 5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        if(message.getLeaderCommit() != null && message.getLeaderCommit() > this.serverState.getCommitIndex()) {
            int commitIndex;
            try {
                commitIndex = Math.min(message.getLeaderCommit(), message.getNewEntries().lastKey());
            } catch(NoSuchElementException e) {
                commitIndex = message.getLeaderCommit();
            }
            this.serverState.setCommitIndex(commitIndex);
        }

        return new Result(message.getRequestNumber(), currentTerm, true);
    }

    public int requestVote(RemoteServerInterface origin, int term, Integer candidateId, Integer lastLogIndex, Integer lastLogTerm) throws RemoteException {
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
        Integer candidateId = message.getCandidateId();
        Integer lastLogIndex = message.getLastLogIndex();

        int currentTerm = serverState.getCurrentTerm();

        // 1. Reply false if term < currentTerm (§5.1)
        if(term < currentTerm) {
            return new Result(message.getRequestNumber(), currentTerm, false);
        }

        // 2. If votedFor is null or candidateId, and candidate’s log is at
        //    least as up-to-date as receiver’s log, grant vote (§5.2, §5.4)
        Integer votedFor = serverState.getVotedFor();
        if((votedFor == null || votedFor.equals(candidateId))
                && lastLogIndex >= serverState.getLastLogIndex()) {
            serverState.setVotedFor(candidateId);

            // Stops [If election timeout elapses without receiving AppendEntries RPC from current leader or granting
            // vote to candidate: convert to candidate]
            this.serverState.receivedMsg(term);

            System.out.println("[ELECTION] Voted for " + candidateId);
            return new Result(message.getRequestNumber(), currentTerm, true);
        }

        return new Result(message.getRequestNumber(), currentTerm, false);
    }

    @Override
    public void reply(Result result) throws RemoteException {
        enqueue(result);
    }

    /**
     * Updates the server state
     * @param next The next state
     */
    public synchronized void updateState(State next) {
        this.serverState.stopTimers();
        this.serverState = next;
    }

    /**
     * Starts election process
     */
    public synchronized void startElection(int term, Integer lastLogIndex, Integer lastLogTerm) {
        Thread thread;
        for (Map.Entry<Integer, RemoteServerInterface> entry : this.cluster.entrySet()) {
            thread = new Thread(() -> askForVote(entry.getValue(), term, lastLogIndex, lastLogTerm));
            thread.start();
            electionThreads.add(thread);
        }

        for(Thread t : electionThreads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        electionThreads.clear();
    }

    /**
     * Send a message to a server to ask for its vote
     * @param server The server
     */
    private synchronized void askForVote(RemoteServerInterface server, int term, Integer lastLogIndex, Integer lastLogTerm){
        try {
            int receipt = server.requestVote(this.selfInterface, term, this.id, lastLogIndex, lastLogTerm);

            outgoingRequests.put(receipt, Message.Type.RequestVote);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the size of the server cluster (considering itself)
     * @return The cluster size
     */
    public int getClusterSize() {
        return cluster.size() + 1; // +1 to consider self
    }

    /**
     * Start keep-alive process
     */
    public void startKeepAlive() {
        System.err.println(this.serverState.getClass());

         Thread thread;
         for(Map.Entry<Integer, RemoteServerInterface> entry: cluster.entrySet()) {
             thread = new Thread(() -> keepAlive(entry.getValue()));
             thread.start();
         }
    }

    /**
     * Keeps alive the connection a connection
     * @param server The remote server interface to keep alive
     */
    public void keepAlive(RemoteServerInterface server) {
        Result result;
        while(true) {
            try {
                // todo do I need the result? Option: keep as separate object, read result, stop if false
//                result = server.appendEntries(this.serverState.getCurrentTerm(), this.id, null,
//                        null, null, null);

                int r = server.appendEntries(this.selfInterface, this.serverState.getCurrentTerm(), this.id, null,
                        null, null, null);

                // If a follower has a different term and thus this is not the leader anymore,
                // the thread stops todo consider
//                if(!result.success) {
//                    return;
//                }
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public String toString() {
        return "{\n" +
                "   'serverState':" + serverState.toString() +
                "',\n   'cluster':'" + cluster.toString() +
                "',\n   'id':'" + id +
                "',\n   'electionThreads':'" + electionThreads.toString() +
                "'\n}";
    }
}
