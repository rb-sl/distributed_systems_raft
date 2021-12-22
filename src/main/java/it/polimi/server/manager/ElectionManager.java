package it.polimi.server.manager;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.Message;
import it.polimi.server.Server;

import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ElectionManager {
    private Server server;
    private final Map<String, RemoteServerInterface> activeCluster;

    int term;
    private Integer lastLogIndex;
    private Integer lastLogTerm;

    private static Thread electionThread;

    /**
     * List of election threads.
     * Used to ask for votes
     */
    private List<Thread> electionThreads;

    public ElectionManager(Server server, Map<String, RemoteServerInterface> cluster, int term, Integer lastLogIndex, Integer lastLogTerm) {
        this.server = server;
        this.activeCluster = cluster;
        this.electionThreads = new ArrayList<>();

        this.term = term;
        this.lastLogIndex = lastLogIndex;
        this.lastLogTerm = lastLogTerm;
    }

    public void startElection() {
        electionThread = new Thread(this::election);
        electionThread.start();
    }

    /**
     * Starts election process
     */
    public void election() {
        Thread thread;

        for (Map.Entry<String, RemoteServerInterface> entry : this.activeCluster.entrySet()) {
            if(!entry.getKey().equals(server.getId())) {
                thread = new Thread(() -> askForVote(entry.getValue(), this.term, this.lastLogIndex, this.lastLogTerm));
                electionThreads.add(thread);
                thread.start();
            }
        }

        for(Thread t : electionThreads) {
            try {
                if(Thread.currentThread().isInterrupted()) {
                    throw new InterruptedException();
                }
                t.join();
            } catch (InterruptedException e) {
                // The server can interrupt the election on a state change or new election
                for(Thread active : electionThreads) {
                    if(active.isAlive()) {
                        active.interrupt();
                    }
                }
                electionThreads.clear();
                System.out.println("Election interrupted");
                return;
            }
        }

        electionThreads.clear();
    }

    /**
     * Send a message to a server to ask for its vote
     * @param remoteServer The server
     */
    private void askForVote(RemoteServerInterface remoteServer, int term, Integer lastLogIndex, Integer lastLogTerm){
        while(!Thread.currentThread().isInterrupted()) {
            try {
                int receipt = remoteServer.requestVote(this.server, term, this.server.getId(), lastLogIndex, lastLogTerm);

                this.server.addRequest(receipt, Message.Type.RequestVote);
                return;
            } catch (RemoteException e) {
                System.err.println("Server unreachable - retrying...");
            }
        }
        System.out.println("Election request interrupted");
    }

    public void interruptElection() {
        if(electionThread != null) {
            electionThread.interrupt();
            electionThread = null;
        }
    }
}
