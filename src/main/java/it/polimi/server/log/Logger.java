package it.polimi.server.log;

import com.google.gson.Gson;
import it.polimi.exceptions.IndexAlreadyDiscardedException;
import it.polimi.server.Server;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Class to manage logs
 */
public class Logger {
    /**
     * The map of LogEntries in function of log index, ordered on the log index
     */
    private SortedMap<Integer, LogEntry> entries;
    /**
     * Synchronization object for entries
     */
    private static final Object entriesSync = new Object();
    
    /**
     * Next index to insert 
     */
    private static Integer nextKey;

    /**
     * The server hosting the logger
     */
    private final Server server;

    /**
     * Persistent storage for variables
     */
    @Getter
    protected final Path storage;

    /**
     * Last taken snapshot
     */
    protected static Snapshot lastSnapshot = null;

    /**
     * Gson object
     */
    protected final Gson gson = new Gson();

    public Logger(Server server) {
        this(server, Paths.get("./configuration/" + server.getId() + "_snapshot.json"));
    }

    protected Logger(Server server, Path storage) {
        this.server = server;
        this.entries = new TreeMap<>();
        this.storage = storage;
        nextKey = 0;
    }
    
    /**
     * Gets the term of the entry at a given position
     * @param position The given position
     * @return The term
     */
    public Integer termAtPosition(Integer position) {
        synchronized (entriesSync) {
            if (position == null) {
                return null;
            }
            if(entries.isEmpty() || !entries.containsKey(position)) {
                if(lastSnapshot != null && lastSnapshot.getLastIncludedIndex().equals(position)) {
                    return lastSnapshot.getLastIncludedTerm();
                }
                else {
                    return null;
                }
            }
            return entries.get(position).getTerm();
        }
    }

    /**
     * Checks whether an entry is in conflict with a new one (same position, but different term)
     * @param position The position
     * @param term The term
     * @return Whether or not there is a conflict
     */
    public Boolean containConflict(Integer position, Integer term) {
        synchronized (entriesSync) {
            return entries.containsKey(position) && (entries.get(position).getTerm() != term);
        }
    }

    /**
     * Deletes from log all entries with key greater or equal than logIndex
     * @param logIndex The log index
     */
    public void deleteFrom(int logIndex) {
        synchronized (entriesSync) {
            entries.entrySet().removeIf(entry -> entry.getKey() >= logIndex);
        }
    }

    /**
     * Append new entries to the old ones.<\br>
     * Entries with the same location of existing ones are ignored
     * @param newEntries The new entries
     */
    public void appendNewEntries(SortedMap<Integer, LogEntry> newEntries) {
        synchronized (entriesSync) {
            newEntries.forEach((key, log) -> entries.putIfAbsent(key, log));
            printLog();
        }
    }

    /**
     * Outputs the current log
     */
    public void printLog() {
        System.out.println("---Log---------------");
        for(Map.Entry<Integer, LogEntry> entry : entries.entrySet()) {
            System.out.println(entry.getKey() + ": [Term " + entry.getValue().getTerm() + "] " + entry.getValue().getVarName() + " ← " + entry.getValue().getValue());
        }
        System.out.println("---------------------");
    }

    /**
     * @see it.polimi.server.state.State#getLastLogIndex() getLastLogIndex
     * @throws NoSuchElementException If entries are empty 
     */
    public Integer getLastIndex() throws NoSuchElementException {
        synchronized (entriesSync) {
            try {
                return entries.lastKey();
            }
            catch(NoSuchElementException e) {
                return null;
            }
        }
    }

    public LogEntry getEntry(Integer index) throws NoSuchElementException {
        return index == null ? null : entries.get(index);
    }

    /**
     * Returns the greater index lower than the given index
     * @param index The given index
     * @return The last index before the given one
     * @throws IndexAlreadyDiscardedException When the map does not contain the given index
     */
    public Integer getIndexBefore(Integer index) throws IndexAlreadyDiscardedException {
        if(index == null) {
            return null;
        }
        try {
            synchronized (entriesSync) {
                if(!entries.containsKey(index)) {
                    throw new IndexAlreadyDiscardedException("Index Before not available", index);
                }
                return entries.headMap(index).lastKey();
            }
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    /**
     * Get entries with key greater or equal than a given one
     * @param next The given key
     * @return The following entries
     * @throws IndexAlreadyDiscardedException When the given key is not present in the map
     */
    public SortedMap<Integer, LogEntry> getEntriesSince(Integer next) throws IndexAlreadyDiscardedException {
        if(next == null) {
            return null;
        }
        synchronized (entriesSync) {
            if(!entries.containsKey(next)) {
                throw new IndexAlreadyDiscardedException("Index not available", next);
            }
            return entries.tailMap(next);
        }
    }

    /**
     * Add an entry to the entries map
     * @param term The entry's term
     * @param variable The variable's name
     * @param value The variable's value
     * @param requestNumber The request number
     * @param clientRequestNumber The client request number
     */
    public void addEntry(int term, String variable, Integer value, Integer requestNumber, Integer clientRequestNumber) {
        synchronized (entriesSync) {
            entries.put(nextKey, new LogEntry(term, variable, value, requestNumber, clientRequestNumber, nextKey));
            nextKey++;
            printLog();
        }
    }

    /**
     * Creates and writes a snapshot of entries, that are then deleted
     */
    public void takeSnapshot() {
        Integer commitIndex = this.server.getServerState().getCommitIndex();
        lastSnapshot = new Snapshot(this.server.getServerState().getVariables(), commitIndex, termAtPosition(commitIndex));
        try {
            writeSnapshot(gson.toJson(lastSnapshot));
        } catch (IOException e) {
            e.printStackTrace();
        }
        
        // Once a server completes writing a snapshot, it may delete all log entries up through the last included 
        // index, as well as any prior snapshot.
        clearUpToIndex(commitIndex, true);
    }

    /**
     * Writes the last commit on file
     * @param toWrite What to write
     * @throws IOException When there's an error in input output functions
     */
    protected void writeSnapshot(String toWrite) throws IOException {
        Files.write(storage, toWrite.getBytes());
    }

    /**
     * Removes all entries up to an index (can be included or excluded)
     * @param lastIncludedIndex Index up to which remove entries
     * @param removeLast Controls whether to remove also the provided index entry
     */
    public void clearUpToIndex(Integer lastIncludedIndex, boolean removeLast) {
        synchronized (entriesSync) {
            entries = entries.tailMap(lastIncludedIndex);
            if(removeLast) {
                entries.remove(lastIncludedIndex);
            }
        }
    }

    /**
     * Synchronized getter for the entries list
     * @return The entries
     */
    public SortedMap<Integer, LogEntry> getEntries() {
        synchronized(entriesSync) {
            return entries;
        }
    }

    /**
     * Removes all entries from the log
     */
    public void clearEntries() {
        synchronized (entriesSync) {
            entries.clear();
        }
    }
}
