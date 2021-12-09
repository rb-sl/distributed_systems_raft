package it.polimi.server.state;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.Message;
import it.polimi.networking.messages.Result;
import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Leader extends State {
    // Volatile state on leaders (Reinitialized after election):
    /**
     * For each server, index of the next log entry to send to that server (initialized to leader last log index + 1)
     */
    private static final Map<String, Integer> nextIndex = new HashMap<>() ;

    /**
     * For each server, index of highest log entry known to be replicated on server
     * (initialized to 0, increases monotonically)
     */
    private static Map<String, Integer> matchIndex;

    private static Map<String, Thread> replicationThreads;

    private final static List<Integer> pendingRequests = new ArrayList<>();

    private static final Map<Integer, Result> receiptResults = new HashMap<>();

    public Leader(State state) {
        this(state.server, state.currentTerm, state.votedFor, state.logger, state.commitIndex, state.lastApplied, state.variables);
    }

    public Leader(Server server, Integer currentTerm, String votedFor, Logger logger, Integer commitIndex, Integer lastApplied, Map<String, Integer> variables) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied, variables);
        this.role = Role.Leader;

        matchIndex = new HashMap<>();
        replicationThreads = new HashMap<>();

        Integer nextLogIndex = getLastLogIndex();
        if(nextLogIndex != null)
            nextLogIndex++;

        for(String id: server.getServersInCluster()) {
            // initialized to leader last log index + 1
            nextIndex.put(id, nextLogIndex);

            // initialized to 0
            matchIndex.put(id, 0);
        }

        System.out.println(Thread.currentThread().getId() + " [!] Changed to LEADER in Term " + currentTerm);

        this.server.setLeader(this.server);

        // Upon election: send initial empty AppendEntries RPCs (heartbeat) to each server; repeat during idle periods
        // to prevent election timeouts (§5.2)
        this.server.startKeepAlive();
        this.startReplication(server.getCluster());
    }

    public void startReplication(Map<String, RemoteServerInterface> cluster) {
        for(Map.Entry<String, RemoteServerInterface> entry: cluster.entrySet()) {
            startReplication(entry.getKey(), entry.getValue());
        }
    }

    public void startReplication(String serverId, RemoteServerInterface serverInterface) {
        Thread thread;

        thread = replicationThreads.get(serverId);
        if(thread != null && thread.isAlive()) {
            thread.interrupt();
            System.out.println(Thread.currentThread().getId() + ": thread " + thread.getId() + " stopped, starting new one");
        }

        thread = new Thread(() -> replicate(serverId, serverInterface));
        replicationThreads.put(serverId, thread);
        thread.start();
    }

    private void replicate(String serverId, RemoteServerInterface serverInterface) {
        while(!Thread.currentThread().isInterrupted()) {
            Integer next;
            synchronized (nextIndex) {
                next = nextIndex.get(serverId);
                if(next == null) {
                    next = 0;
                }
            }
            Integer lastLogIndex = getLastLogIndex();

            // If last log index ≥ nextIndex for a follower:
            if (lastLogIndex != null && lastLogIndex >= next) {
                // send AppendEntries RPC with log entries starting at nextIndex
                Integer prevLogIndex = logger.getIndexBefore(next);
                int receipt;
                try {
                    receipt = serverInterface.appendEntries(this.server, currentTerm, this.server.getId(),
                            prevLogIndex, logger.termAtPosition(prevLogIndex), logger.getEntriesSince(next), commitIndex);
                    pendingRequests.add(receipt);
                    this.server.addRequest(receipt, Message.Type.AppendEntry);
                } catch (RemoteException e) {
                    // Retries indefinitely
                    continue;
                }

                while (!receiptResults.containsKey(receipt)) {
                    try {
                        synchronized (receiptResults) {
                            receiptResults.wait();
                        }
                    } catch (InterruptedException e) {
                        System.err.println("Replication thread " + Thread.currentThread().getId() + " interrupted");
                        return;
                    }
                }

                Result result = receiptResults.remove(receipt);
                if(result.isSuccess()) {
                    // If successful: update nextIndex and matchIndex for follower (§5.3)
                    synchronized (nextIndex) {
                        nextIndex.put(serverId, lastLogIndex + 1);
                    }
                    matchIndex.put(serverId, lastLogIndex);
                    System.out.println(Thread.currentThread().getId() + "Replication " + serverId + " ok, new next: " + nextIndex.get(serverId));
                }
                else {
                    // If AppendEntries fails because of log inconsistency:
                    // decrement nextIndex and retry (§5.3)
                    nextIndex.put(serverId, nextIndex.get(serverId) - 1);
                    System.err.println("Replication " + serverId + " no");
                }
            }
            else {
                try {
                    synchronized (nextIndex) {
                        System.err.println("No new requests");
                        nextIndex.wait();
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void processResult(Message.Type type, Result result) {
        super.processResult(type, result);

        Integer requestNumber = result.getRequestNumber();
        if (type == Message.Type.AppendEntry && pendingRequests.contains(requestNumber)) {
            synchronized (pendingRequests) {
                pendingRequests.remove(requestNumber);
            }
            synchronized (receiptResults) {
                receiptResults.put(requestNumber, result);
                receiptResults.notifyAll();
            }
        }
    }

    public void logAdded() {
        synchronized (nextIndex) {
            nextIndex.notifyAll();
        }
    }
}
