package it.polimi.server;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.Result;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

public class keepAliveManager {
    private static final int KEEPALIVE_INTERVAL = 10;

    private final Server server;
    private final Map<String, RemoteServerInterface> cluster;
    private static Map<String, Thread> threads;

    public keepAliveManager(Server server, Map<String, RemoteServerInterface> cluster) {
        this.server = server;
        this.cluster = cluster;
        threads = new HashMap<>();
    }

    /**
     * Start keep-alive process
     */
    public void startKeepAlive() {
        for(Map.Entry<String, RemoteServerInterface> entry: cluster.entrySet()) {
            startKeepAlive(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Starts or resets a keepalive
     * @param serverId The name of the server to keep alive
     * @param serverInterface The remote server's interface
     */
    public synchronized void startKeepAlive(String serverId, RemoteServerInterface serverInterface) {
        Thread thread;

        // If the leader was already keeping alive the remote server, the thread is swapped for the new one
        thread = threads.get(serverId);
        if(thread != null && thread.isAlive()) {
            thread.interrupt();
            System.out.println(Thread.currentThread().getId() + ": thread " + thread.getId() + " stopped, starting new one");
        }

        thread = new Thread(() -> keepAlive(serverId, serverInterface));

        threads.put(serverId, thread);
        thread.start();
    }

    /**
     * Keeps alive a connection
     * @param serverInterface The remote server interface to keep alive
     */
    private void keepAlive(String serverId, RemoteServerInterface serverInterface) {
        Integer term = this.server.getServerState().getCurrentTerm();
        String originId = this.server.getId();

        while(!Thread.currentThread().isInterrupted()) {
            try {
                serverInterface.appendEntries(this.server, term, originId, null,
                        null, null, null);
            } catch (RemoteException e) {
                // Host unreachable
                System.err.println(Thread.currentThread().getId() + " [KeepAlive] " + serverId + " unreachable");
            }

            try {
                Thread.sleep(KEEPALIVE_INTERVAL);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
