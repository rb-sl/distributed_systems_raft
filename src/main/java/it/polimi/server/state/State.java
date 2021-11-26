package it.polimi.server.state;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import it.polimi.server.log.LogEntry;
import it.polimi.server.log.Logger;
import lombok.Getter;
import lombok.Setter;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;

public abstract class State {
    // Persistent state on all servers (Updated on stable storage before responding to RPCs)
    /**
     * Latest term server has seen (initialized to 0 on first boot, increases monotonically)
     */
    @Getter @Setter
    private int currentTerm;
    /**
     * CandidateId that received vote in current term (or null if none)
     */
    @Getter @Setter
    private Integer votedFor;
    /**
     * Log entries; each entry contains command for state machine, and term when entry was received by leader (first index is 1)
     */
    @Getter @Setter
    private Logger logger;

    // Volatile state on all servers:

    /**
     * Index of highest log entry known to be committed (initialized to 0, increases monotonically)
     */
    @Getter
    private int commitIndex;

    /**
     * Index of highest log entry applied to state machine (initialized to 0, increases monotonically)
     */
    @Getter @Setter
    private int lastApplied;

    /**
     * Stored variables
     */
    private Map<String, Integer> variables;

    /**
     * Persistent storage for variables
     */
    Path storage;

    FileOutputStream fileOutputStream;
    OutputStreamWriter outputStreamWriter;

    Gson gson;

    public State() {
        this.setCommitIndex(0);
        this.setCurrentTerm(-1);
        this.setVotedFor(null);
        this.setLogger(new Logger());

        this.setCommitIndex(0);
        this.setLastApplied(0);

        this.gson = new Gson();

        Path path = Paths.get("./storage.json");

        ClassLoader classLoader = getClass().getClassLoader();
        this.storage = Paths.get("./storage.json");
        restoreVars();
    }

    /**
     * Restores the variables map after a restart
     */
    private void restoreVars() {
        try {
            Reader reader = new InputStreamReader(Objects.requireNonNull(this.getClass().getResourceAsStream("/storage.json")));
            Type type = new TypeToken<Map<String, Integer>>(){}.getType();
            this.variables = gson.fromJson(reader, type);
            reader.close();
        } catch(NullPointerException | JsonIOException | IOException e) {
            e.printStackTrace();
        } catch (JsonSyntaxException e) {
            // With a corrupted file variables are reinitialized
            this.variables = new HashMap<>();
        }
        System.out.println(this.variables);
    }

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

    }
}
