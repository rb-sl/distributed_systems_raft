package it.polimi.server.state;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.Message;
import it.polimi.networking.messages.Result;
import it.polimi.networking.messages.UpdateIndex;
import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.rmi.RemoteException;
import java.util.*;

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
    private static final Object receiptSync = new Object();

    public Leader(State state) {
        this(state.server, state.currentTerm, state.votedFor, state.logger, commitIndex, state.lastApplied);
    }

    public Leader(Server server, Integer currentTerm, String votedFor, Logger logger, Integer commitIndex, Integer lastApplied) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied);
        this.role = Role.Leader;

        matchIndex = new HashMap<>();
        replicationThreads = new HashMap<>();

        Integer lastIndex = getLastLogIndex();

        Integer nextLogIndex = null;
        if(lastIndex != null) {
            nextLogIndex = lastIndex + 1;
        }

//        nextIndex.put(server.getId(), nextLogIndex);
//        matchIndex.put(server.getId(), lastIndex);

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
                Integer commit;
                synchronized (commitIndexSync) {
                    commit = commitIndex;
                }
                int receipt;
                try {
                    synchronized (pendingRequests) {
                        receipt = serverInterface.appendEntries(this.server, currentTerm, this.server.getId(),
                                prevLogIndex, logger.termAtPosition(prevLogIndex), logger.getEntriesSince(next), commit);

                        pendingRequests.add(receipt);
                    }
                    this.server.addRequest(receipt, Message.Type.AppendEntry);
                } catch (RemoteException e) {
                    // Retries indefinitely
                    continue;
                }

                Result result;
                synchronized (receiptSync) {
                    while (!receiptResults.containsKey(receipt)) {
                        try {
                            receiptSync.wait();
                        } catch (InterruptedException e) {
                            System.err.println("Replication thread " + Thread.currentThread().getId() + " interrupted");
                            return;
                        }
                    }
                    result = receiptResults.remove(receipt);
                }

                if(result.isSuccess()) {
                    // If successful: update nextIndex and matchIndex for follower (§5.3)
                    synchronized (nextIndex) {
                        nextIndex.put(serverId, lastLogIndex + 1);
                    }
                    synchronized (commitIndexSync) {
                        matchIndex.put(serverId, lastLogIndex);
                    }
                    System.out.println(Thread.currentThread().getId() + "Replication " + serverId + " ok, new next: " + nextIndex.get(serverId));
                    checkCommitIndex();
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
                        // When no new client requests need to be replicated the thread waits
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
     * If commitIndex update rules are met a message is enqueued on the leader to update the index
     */
    public void checkCommitIndex() {
        Integer minIndexGTCommit = null;
        int minCount = 0;

        synchronized (commitIndexSync) {
            for (Integer i : matchIndex.values()) {
                if (commitIndex == null || i > commitIndex) {
                    if (minIndexGTCommit == null || i < minIndexGTCommit) {
                        minIndexGTCommit = i;
                        minCount = 1;
                    } else if (i.equals(minIndexGTCommit)) {
                        minCount++;
                    }
                }
            }

            // If there exists an N such that N > commitIndex,
            if (minIndexGTCommit != null
                    // a majority of matchIndex[i] ≥ N,
                    && minCount > matchIndex.values().size() / 2
                    // and log[N].term == currentTerm:
                    && (logger.getEntry(minIndexGTCommit) != null && logger.getEntry(minIndexGTCommit).getTerm() == currentTerm)) {
                // set commitIndex = N (§5.3, §5.4).
                this.server.enqueue(new UpdateIndex(minIndexGTCommit));
                System.err.println(Thread.currentThread().getId() + " INDEX UPDATED: " + minIndexGTCommit);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void processResult(Message.Type type, Result result) {
        super.processResult(type, result);

        Integer requestNumber = result.getInternalRequestNumber();
        synchronized (pendingRequests) {
            if (type == Message.Type.AppendEntry && pendingRequests.contains(requestNumber)) {
                // Wakes up threads waiting for follower's answers
                pendingRequests.remove(requestNumber);

                synchronized (receiptSync) {
                    receiptResults.put(requestNumber, result);
                    receiptSync.notifyAll();
                }
            }
        }
    }

    public void logAdded() {
//        synchronized (commitIndexSync) {
//            matchIndex.put(server.getId(), getLastLogIndex());
//        }
        synchronized (nextIndex) {
            nextIndex.notifyAll();
        }
    }
}
