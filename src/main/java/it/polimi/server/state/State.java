package it.polimi.server.state;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import it.polimi.networking.messages.Message;
import it.polimi.networking.messages.Result;
import it.polimi.networking.messages.StateTransition;
import it.polimi.server.Server;
import it.polimi.server.log.LogEntry;
import it.polimi.server.log.Logger;
import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;

public abstract class State {

    /**
     *The state's role
     */
    public enum Role {
        Leader, Follower, Candidate
    }

    /**
     * The election timeout
     */
    protected static final int ELECTION_TIMEOUT = 350;

    // Persistent state on all servers (Updated on stable storage before responding to RPCs)
    /**
     * Latest term server has seen (initialized to 0 on first boot, increases monotonically)
     */
    @Getter @Setter
    protected int currentTerm;

    /**
     * CandidateId that received vote in current term (or null if none)
     */
    @Getter @Setter
    protected Integer votedFor;

    /**
     * Log entries; each entry contains command for state machine, and term when entry was received by leader (first index is 1)
     */
    @Getter @Setter
    protected Logger logger;

    // Volatile state on all servers:

    /**
     * Index of highest log entry known to be committed (initialized to 0, increases monotonically)
     */
    @Getter
    protected int commitIndex;

    /**
     * Index of highest log entry applied to state machine (initialized to 0, increases monotonically)
     */
    @Getter @Setter
    protected int lastApplied;

    /**
     * Stored variables
     */
    protected Map<String, Integer> variables; // TODO Maybe use as synchronized?

    /**
     * Persistent storage for variables
     */
    protected final Path storage = Paths.get("./storage.json");

    /**
     * Gson object
     */
    protected final Gson gson = new Gson();

    /**
     * Reference to the state's server
     */
    protected Server server;

    /**
     * Init constructor
     * @param server The server
     */
    public State(Server server) {
        this.setCommitIndex(0);
        this.setCurrentTerm(-1);
        this.setVotedFor(null);
        this.setLogger(new Logger());

        this.setCommitIndex(0);
        this.setLastApplied(0);

        restoreVars();
        this.server = server;
    }

    /**
     * Parametric constructor
     * @param server The server
     * @param currentTerm The current term
     * @param votedFor Candidate that received vote on current term
     * @param logger The logger
     * @param commitIndex Index of highest log entry known to be committed
     * @param lastApplied Index of highest log entry applied to state machine
     * @param variables Map of variables
     */
    public State(Server server, int currentTerm, Integer votedFor, Logger logger, int commitIndex, int lastApplied,
                 Map<String, Integer> variables) {
        this.server = server;
        this.currentTerm = currentTerm;
        this.votedFor = votedFor;
        this.logger = logger;
        this.commitIndex = commitIndex;
        this.lastApplied = lastApplied;
        this.variables = variables;

        server.updateState(this);
    }

    /**
     * Restores the variables map after a restart
     */
    private void restoreVars() {
        try {
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            this.variables = gson.fromJson(Files.readString(storage), type);
        } catch(NullPointerException | JsonIOException | IOException e) {
            e.printStackTrace();
        } catch (JsonSyntaxException e) {
            // With a corrupted file variables are reinitialized
            this.variables = new HashMap<>();
        }
        System.out.println(this.variables);
    }

    /**
     *Write the last commit on file
     * @param toWrite What to write
     * @throws IOException When there's an error in input output functions
     */
    private void writeCommit(String toWrite) throws IOException {
        Files.write(storage, toWrite.getBytes());
    }

    /**
     * Increase current term value
     */
    public void increaseCurrentTerm() {
        this.currentTerm++;
    }

    /**
     * Gets the last index of the logs map
     * @return The last map key
     */
    public int getLastLogIndex() {
        try{
            return logger.getLastIndex();
        } catch(NoSuchElementException e) {
            return -1;
        }
    }

    /**
     * Setter for commitIndex, with a check on unapplied logs
     * @param commitIndex The commit index
     */
    public void setCommitIndex(int commitIndex) {
        this.commitIndex = commitIndex;

        // If commitIndex > lastApplied: increment lastApplied, apply log[lastApplied] to state machine (§5.3)
        if(this.commitIndex > this.lastApplied) {
            for(int i = lastApplied + 1; i <= this.commitIndex; i++) {
                try {
                    applyToStateMachine(this.logger.getEntry(i));
                } catch(NoSuchElementException e) {
                    // Should not happen as committed entries should be known to the server
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Apply the entry to the machine state
     * @param entry The entry
     */
    private void applyToStateMachine(LogEntry entry) {
        String key = entry.getVarName();
        Integer val = entry.getValue();

        this.variables.put(key, val);
        try {
            writeCommit(gson.toJson(variables));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Set state to follower when the term is greater than {@link State#currentTerm}
     * @param term The term of received message
     */
    public void receivedMsg(int term) {
        // If RPC request or response contains term T > currentTerm: set currentTerm = T, convert to follower (§5.1)
        if(term > currentTerm) {
            currentTerm = term;
            this.server.enqueue(new StateTransition(Role.Follower));
        }
    }

    /**
     * Receives an append message
     * @param term The term
     */
    public void receivedAppend(int term) {
        receivedMsg(term);
    }

    /**
     * Increment the count of received votes
     */
    public synchronized void incrementVotes() {}

    /**
     * Process the message's result
     * @param type The message type
     * @param result The result
     */
    public void processResult(Message.Type type, Result result) {
        receivedMsg(result.getTerm());
    }

    /**
     * Stop running election timers
     */
    public void stopTimers() {}

    @Override
    public String toString() {
        return "State{\n" +
                "       'state': '" + this.getClass() +
                "',\n       'currentTerm':'" + currentTerm +
                "',\n        'votedFor':'" + votedFor +
                "',\n        'logger':'" + logger +
                "',\n        'commitIndex':'" + commitIndex +
                "',\n        'lastApplied':'" + lastApplied +
                "',\n        'variables':'" + variables +
                "',\n        'storage':'" + storage +
                "'\n    }";
    }
}
