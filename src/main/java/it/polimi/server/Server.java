package it.polimi.server;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.Result;
import it.polimi.server.log.LogEntry;
import it.polimi.server.state.Follower;
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

    public Server() {
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
    }

    /**
     * {@inheritDoc}
     */
    public int addToCluster(RemoteServerInterface follower) throws RemoteException {
        int id = ThreadLocalRandom.current().nextInt(0, 10000);

        for(Map.Entry<Integer, RemoteServerInterface> entry: cluster.entrySet()) {
            entry.getValue().addToCluster(id, follower);
        }

        addToCluster(id, follower);

        return id;
    }

    public void addToCluster(int id, RemoteServerInterface follower) {
        cluster.put(id, follower);
    }

    /**
     * {@inheritDoc}
     */
    public Result appendEntries(int term, Integer leaderId, Integer prevLogIndex, Integer prevLogTerm, SortedMap<Integer, LogEntry> newEntries, Integer leaderCommit) {
        int currentTerm = this.serverState.getCurrentTerm();

        // Stops [If election timeout elapses without receiving AppendEntries RPC from current leader or granting
        // vote to candidate: convert to candidate]
        this.serverState.receivedMsg(term);

        // 1. Reply false if term < currentTerm (§5.1)
        if(term < currentTerm) {
            return new Result(currentTerm, false);
        }

        // 2. Reply false if log does not contain an entry at prevLogIndex
        // whose term matches prevLogTerm (§5.3)
        if(prevLogTerm != null && !prevLogTerm.equals(this.serverState.getLogger().termAtPosition(prevLogIndex))) {
            return new Result(currentTerm, false);
        }

        // 3. If an existing entry conflicts with a new one (same index but different terms),
        // delete the existing entry and all that follow it (§5.3)
        for(Map.Entry<Integer, LogEntry> entry : newEntries.entrySet()) {
            if(this.serverState.getLogger().containConflict(entry.getKey(), entry.getValue().getTerm())) {
                this.serverState.getLogger().deleteFrom(entry.getKey());
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
            return new Result(currentTerm, true);
        }

        return new Result(currentTerm, false);
    }

    /**
     * Updates the server state
     * @param next The next state
     */
    public void updateState(State next) {
        this.serverState = next;
    }

    public void requestElection(Thread starter) {
        for (Map.Entry<Integer, RemoteServerInterface> entry : this.cluster.entrySet()) {

        }
    }

    private void askForVote(Thread starter, RemoteServerInterface server){
        try {
            Result r = server.requestVote(this.serverState.getCurrentTerm(), this.id, this.serverState.getLastLogIndex(), this.serverState.getLogger().getLastIndex());
            if(r.success()) {
                this.serverState.incrementVotes(starter);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }

    public int getClusterSize() {
        return cluster.size();
    }
}
