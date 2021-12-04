package it.polimi.server.state;

import it.polimi.networking.RemoteServerInterface;
import it.polimi.networking.messages.Message;
import it.polimi.networking.messages.Result;
import it.polimi.server.Server;
import it.polimi.server.log.Logger;

import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;

public class Leader extends State {
    // Volatile state on leaders (Reinitialized after election):
    /**
     * For each server, index of the next log entry to send to that server (initialized to leader last log index + 1)
     */
    private Map<Integer, Integer> nextIndex;

    /**
     * for each server, index of highest log entry known to be replicated on server
     * (initialized to 0, increases monotonically)
     */
    private Map<Integer, Integer> matchIndex;

    public Leader(State state) {
        this(state.server, state.currentTerm, state.votedFor, state.logger, state.commitIndex, state.lastApplied, state.variables);
    }

    public Leader(Server server, int currentTerm, Integer votedFor, Logger logger, int commitIndex, int lastApplied, Map<String, Integer> variables) {
        super(server, currentTerm, votedFor, logger, commitIndex, lastApplied, variables);
        nextIndex = new HashMap<>();
        matchIndex = new HashMap<>();

        System.out.println(Thread.currentThread().getId() + " [!] Changed to LEADER");

        this.server.setLeader(this.server);
        this.server.startKeepAlive();
    }

    /**
     * {@inheritDoc}
     */
    public void processResult(Message.Type type, Result result) {
        if (type == Message.Type.AppendEntry) {
            if(result.isSuccess()) {
                incrementVotes();
            }
        }
    }
}
