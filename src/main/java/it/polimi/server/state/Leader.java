package it.polimi.server.state;

import it.polimi.exceptions.IndexAlreadyDiscardedException;
import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.Message;
import it.polimi.networking.messages.Result;
import it.polimi.networking.messages.UpdateIndex;
import it.polimi.server.Server;
import it.polimi.server.log.Logger;
import it.polimi.server.log.Snapshot;

import java.rmi.RemoteException;
import java.util.*;

/**
 * State for leaders
 */
public class Leader extends State {
    // Volatile state on leaders (Reinitialized after election):
    /**
     * For each server, index of the next log entry to send to that server (initialized to leader last log index + 1)
     */
    private static final Map<String, Integer> nextIndex = new HashMap<>();
    /**
     * Synchronization object for nextIndex
     */
    private static final Object nextIndexSync = new Object();

    /**
     * For each server, index of highest log entry known to be replicated on server
     * (initialized to 0, increases monotonically)
     */
    private static Map<String, Integer> matchIndex;

    /**
     * Threads handling the replication
     */
    private static Map<String, Thread> replicationThreads;

    /**
     * Map of server interfaces at thread start
     */
    private static final Map<String, RemoteServerInterface> activeCluster = new HashMap<>();

    /**
     * List of requests not yet completed
     */
    private final static List<Integer> pendingRequests = new ArrayList<>();
    
    /**
     * Results associated to each receipt
     */
    private static final Map<Integer, Result> receiptResults = new HashMap<>();
    /**
     * Synchronization object for receiptResults
     */
    private static final Object receiptSync = new Object();

    /**
     * Describes if followers confirmed the read
     */
    private static Map<String, Boolean> readConfirmed;
    /**
     * Synchronization object for readConfirmed
     */
    private static final Object readConfirmedSync = new Object();

    public Leader(State state) {
        this(state.server, state.currentTerm, state.votedFor, state.logger, commitIndex, state.lastApplied);
    }

    public Leader(Server server, Integer currentTerm, String votedFor, Logger logger, Integer commitIndex, Integer lastApplied) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied);
        this.role = Role.Leader;

        matchIndex = new HashMap<>();
        replicationThreads = new HashMap<>();
        readConfirmed = new HashMap<>();

        Integer lastIndex = getLastLogIndex();

        Integer nextLogIndex = null;
        if(lastIndex != null) {
            nextLogIndex = lastIndex + 1;
        }

//        nextIndex.put(server.getId(), nextLogIndex); todo same
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
        
        // [...] a leader must have the latest information on which entries are committed. The Leader Completeness 
        // Property guarantees that a leader has all committed entries, but at the start of its term, it may not know 
        // which those are. To find out, it needs to commit an entry from its term. Raft handles this by having each 
        // leader commit a blank no-op entry into the log at the start of its term.
        getLogger().addEntry(currentTerm, null, null, null, null);
        logAdded();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean needsConfirmation(String serverId) {
        synchronized (readConfirmedSync) {
            Boolean result = readConfirmed.get(serverId);
            return result == null || !result;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void confirmAppend(String serverId) {
        synchronized (readConfirmedSync) {
            readConfirmed.put(serverId, true);
            readConfirmedSync.notifyAll();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void waitForConfirmation() {
        synchronized (readConfirmedSync) {
            readConfirmed.replaceAll((id, x) -> x = Boolean.FALSE);
            
            // +1 on both sides to account for the leader itself
            while (readConfirmed.values().stream().filter(x -> x).count() + 1 <= (readConfirmed.size() + 1) / 2) {
                try {
                    readConfirmedSync.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            System.out.println("Confirmed " + (readConfirmed.values().stream().filter(x -> x).count() + 1) + "/" + (readConfirmed.size() + 1));
        }        
    }

    /**
     * Starts replicating to all cluster
     * @param cluster The target servers
     */
    public void startReplication(Map<String, RemoteServerInterface> cluster) {
        for(Map.Entry<String, RemoteServerInterface> entry: cluster.entrySet()) {
                startReplication(entry.getKey(), entry.getValue());
        }
    }

    /**
     * {@inheritDoc}
     */
    public void startReplication(String serverId, RemoteServerInterface serverInterface) {
        Thread thread;

        thread = replicationThreads.get(serverId);
        if(thread != null && thread.isAlive()) {
            if(serverInterface.equals(activeCluster.get(serverId))) {
                // The replication is active on the correct interface
                return;
            }
            thread.interrupt();
            System.out.println(Thread.currentThread().getId() + " [Replication] Thread " + thread.getId());
        }

        thread = new Thread(() -> replicate(serverId, serverInterface));
        thread.setDaemon(true);
       
        replicationThreads.put(serverId, thread);
        activeCluster.put(serverId, serverInterface);
        thread.start();
        System.out.println(Thread.currentThread().getId() + " [Replication] Thread " + thread.getId() + " started for " + serverId);
    }

    /**
     * Sends appendEntries and InstallSnapshots to the server
     * @param serverId The target server
     * @param serverInterface The target interface
     */
    private void replicate(String serverId, RemoteServerInterface serverInterface) {
        while(!Thread.currentThread().isInterrupted()) {
            Integer next;
            synchronized (nextIndexSync) {
                next = nextIndex.get(serverId);
                if(next == null) {
                    next = 0;
                }
            }
            Integer lastLogIndex = getLastLogIndex();

            // If last log index ≥ nextIndex for a follower:
            if (lastLogIndex != null && lastLogIndex >= next) {
                // send AppendEntries RPC with log entries starting at nextIndex
                Integer prevLogIndex = null;
                try {
                    prevLogIndex = logger.getIndexBefore(next);
                } catch (IndexAlreadyDiscardedException e) {
                    sendSnapshot(serverId, lastLogIndex, serverInterface);
                    continue;
                }
                Integer commit;
                synchronized (commitIndexSync) {
                    commit = commitIndex;
                }
                
                Integer receipt;
                try {
                    try {
                        receipt = this.server.nextRequestNumber();
                        synchronized (receiptSync) {
                            pendingRequests.add(receipt);
                        }
                        this.server.addRequest(serverId, receipt, Message.Type.AppendEntry);

                        serverInterface.appendEntries(this.server, receipt, currentTerm, this.server.getId(),
                                    prevLogIndex, logger.termAtPosition(prevLogIndex), logger.getEntriesSince(next), commit);
                        waitForResult(serverId, lastLogIndex, receipt);
                    } catch (IndexAlreadyDiscardedException e) {
                        sendSnapshot(serverId, lastLogIndex, serverInterface);
                    }
                } catch (RemoteException e) {
                    // Retries indefinitely
                    continue;
                }       
            }
            else {
                try {
                    synchronized (nextIndexSync) {
                        // When no new client requests need to be replicated the thread waits
                        System.err.println(Thread.currentThread().getId() + ": No new requests");
                        nextIndexSync.wait();
                    }
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    /**
     * Waits for follower results
     * @param serverId The target server
     * @param lastLogIndex The leader's last log index
     * @param receipt The request's receipt
     */
    private void waitForResult(String serverId, Integer lastLogIndex, Integer receipt) {
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
            synchronized (nextIndexSync) {
                nextIndex.put(serverId, lastLogIndex + 1);
            }
            synchronized (commitIndexSync) {
                matchIndex.put(serverId, lastLogIndex);
            }
            System.out.println(Thread.currentThread().getId() + ": Replication " + serverId + " ok, new next = " + nextIndex.get(serverId));
            checkCommitIndex();
        }
        else {
            // If AppendEntries fails because of log inconsistency:
            // decrement nextIndex and retry (§5.3)
            synchronized (nextIndexSync) {
                nextIndex.put(serverId, nextIndex.get(serverId) - 1);
            }
            System.err.println(Thread.currentThread().getId() + ": Replication " + serverId + " failed");
        }
    }

    /**
     * Send snapshot capturing until index lastLogIndex to a given server
     * @param serverId The recipient server id
     * @param lastLogIndex The last index in the snapshot
     * @param serverInterface Target server
     */
    private void sendSnapshot(String serverId, Integer lastLogIndex, RemoteServerInterface serverInterface) {
        // [...] the leader must occasionally send snapshots to followers that lag behind. 
        // This happens when the leader has already discarded the next log entry that it needs to
        // send to a follower.
        System.out.println(Thread.currentThread().getId() + ": Sending snapshot to " + serverId);

        Snapshot snapshot = new Snapshot(this.getVariables(), this.getLastLogIndex(),
                this.logger.termAtPosition(this.logger.getLastIndex()));
        byte[] data = gson.toJson(snapshot).getBytes();

        Integer receipt;
        int iterations = (int) Math.ceil((float) data.length / Snapshot.CHUNK_DIMENSION);
        for (int i = 0; i < iterations; i++) {
            byte[] dataChunk = new byte[Snapshot.CHUNK_DIMENSION];

            try {
                System.arraycopy(data, i * Snapshot.CHUNK_DIMENSION, dataChunk, 0, Snapshot.CHUNK_DIMENSION);
            } catch (ArrayIndexOutOfBoundsException ex) {
                dataChunk = new byte[data.length - i * Snapshot.CHUNK_DIMENSION];
                System.arraycopy(data, i * Snapshot.CHUNK_DIMENSION, dataChunk, 0, data.length - i * Snapshot.CHUNK_DIMENSION);
            }

            boolean done = false;
            do {
                try {
                    receipt = this.server.nextRequestNumber();
                    synchronized (receiptSync) {
                        pendingRequests.add(receipt);
                    }
                    this.server.addRequest(serverId, receipt, Message.Type.InstallSnapshot);
                   
                    serverInterface.installSnapshot(this.server, receipt, currentTerm, this.server.getId(),
                            snapshot.getLastIncludedIndex(), snapshot.getLastIncludedTerm(),
                            i, dataChunk, i == iterations - 1);
                    waitForResult(serverId, lastLogIndex, receipt);
                    
                    done = true;
                } catch (RemoteException e) {
                    continue;
                }
            } while (!done);
        }
    }
    
    /**
     * If commitIndex update rules are met, a message is enqueued on the leader to update the index
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
                    && minCount + 1 > (matchIndex.values().size() + 1) / 2 // Accounts for leader
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
        synchronized (receiptSync) {
            if ((type == Message.Type.AppendEntry || type == Message.Type.InstallSnapshot) && pendingRequests.contains(requestNumber)) {
                // Wakes up threads waiting for follower's answers
                pendingRequests.remove(requestNumber);
                receiptResults.put(requestNumber, result);
                receiptSync.notifyAll();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void logAdded() {
//        synchronized (commitIndexSync) {
//            matchIndex.put(server.getId(), getLastLogIndex());
//        }
        synchronized (nextIndexSync) {
            nextIndexSync.notifyAll();
        }
    }

    /**
     * Stops the messages
     */
    public void stopReplication() {
        for(Thread thread: replicationThreads.values()) {
            thread.interrupt();
        }
        replicationThreads.clear();
    }
}
