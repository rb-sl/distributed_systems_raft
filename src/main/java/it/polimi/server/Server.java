package it.polimi.server;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.Result;
import it.polimi.server.log.LogEntry;
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
    }

    /**
     * {@inheritDoc}
     */
    public RemoteServerInterface getLeader() {
        return this.selfInterface;
    } //todo change to actual leader

    /**
     * {@inheritDoc}
     */
    public int addToCluster(RemoteServerInterface follower) throws RemoteException {
        int id = ThreadLocalRandom.current().nextInt(0, 10000); // todo while(exists) repeat
        System.err.println("Adding to cluster");

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
     * {@inheritDoc}
     */
    public Result appendEntries(int term, Integer leaderId, Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> newEntries, Integer leaderCommit) {
        int currentTerm = this.serverState.getCurrentTerm();

        // Stops [If election timeout elapses without receiving AppendEntries RPC from current leader or granting
        // vote to candidate: convert to candidate]
        this.serverState.receivedAppend(term);

        // 1. Reply false if term < currentTerm (§5.1)
        if(term < currentTerm) {
            System.out.println("AppendEntries ignored: term " + term + " < " + currentTerm);
            return new Result(currentTerm, false);
        }

        // 2. Reply false if log does not contain an entry at prevLogIndex
        // whose term matches prevLogTerm (§5.3)
        if(prevLogTerm != null && !prevLogTerm.equals(this.serverState.getLogger().termAtPosition(prevLogIndex))) {
            System.out.println("AppendEntries ignored: no log entry at prevLogIndex = " + prevLogIndex);
            return new Result(currentTerm, false);
        }

        // 3. If an existing entry conflicts with a new one (same index but different terms),
        // delete the existing entry and all that follow it (§5.3)
        for(Map.Entry<Integer, LogEntry> entry : newEntries.entrySet()) {
            if(this.serverState.getLogger().containConflict(entry.getKey(), entry.getValue().getTerm())) {
                this.serverState.getLogger().deleteFrom(entry.getKey()); // todo works also for thread 1?
            }
        }

        // 4. Append any new entries not already in the log
        this.serverState.getLogger().appendNewEntries(newEntries);

        // 5. If leaderCommit > commitIndex, set commitIndex = min(leaderCommit, index of last new entry)
        if(leaderCommit > this.serverState.getCommitIndex()) {
            int commitIndex;
            try {
                commitIndex = Math.min(leaderCommit, newEntries.lastKey());
            } catch(NoSuchElementException e) {
                commitIndex = leaderCommit;
            }
            this.serverState.setCommitIndex(commitIndex);
        }

        return new Result(currentTerm, true);
    }

    /**
     * {@inheritDoc}
     */
    public Result requestVote(int term, Integer candidateId, Integer lastLogIndex, Integer lastLogTerm) throws RemoteException {
        int currentTerm = serverState.getCurrentTerm();
        // 1. Reply false if term < currentTerm (§5.1)
        if(term < currentTerm) {
            return new Result(currentTerm, false);
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
            return new Result(currentTerm, true);
        }

        return new Result(currentTerm, false);
    }

    /**
     * Updates the server state
     * @param next The next state
     */
    public synchronized void updateState(State next) {
        this.serverState = next;
    }

    /**
     * Starts election process
     */
    public synchronized void startElection() {
        for(Thread t: electionThreads) {
            t.interrupt();
        }
        electionThreads.clear();

        Thread thread;
        for (Map.Entry<Integer, RemoteServerInterface> entry : this.cluster.entrySet()) {
            thread = new Thread(() -> askForVote(entry.getValue()));
            thread.start();
            electionThreads.add(thread);
        }
    }

    /**
     * Send a message to a server to ask for its vote
     * @param server The server
     */
    private synchronized void askForVote(RemoteServerInterface server){
        try {
            int term;
            try {
                term = this.serverState.getLogger().getLastIndex();
            } catch (NoSuchElementException e) {
                term = -1;
            }
            Result r = server.requestVote(this.serverState.getCurrentTerm(), this.id, this.serverState.getLastLogIndex(), term);
            if(r.success()) {
                this.serverState.incrementVotes();
            }
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
     * @param r The remote server interface to keep alive
     */
    public void keepAlive(RemoteServerInterface r) {
        System.err.println(this.serverState.getClass());
        while(this.serverState.getClass().toString().equals(Leader.class.toString())) {
            try {
                r.appendEntries(this.serverState.getCurrentTerm(), this.id, null,
                        null, null, null);
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
}
