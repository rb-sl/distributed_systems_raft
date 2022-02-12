package it.polimi.server.log;

import com.google.gson.Gson;
import it.polimi.server.Server;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class Logger {
    /**
     * The map of LogEntries in function of log index
     * The map is ordered on the log index
     */
    private SortedMap<Integer, LogEntry> entries;
    private static Integer nextKey;
    private static final Object entriesSync = new Object();
    
    private final Server server;

    /**
     * Persistent storage for variables
     */
    @Getter
    protected final Path storage;
    
    private static Snapshot lastSnapshot;

    /**
     * Gson object
     */
    protected final Gson gson = new Gson();

    public Logger(Server server) {
        this.server = server;
        this.entries = new TreeMap<>();
        this.storage = Paths.get("./configuration/" + server.getId() + "_snapshot.json");
        nextKey = 0;
    }

    /**
     * Checks whether or not the log has an entry (on given term) with the given log index
     * @param position The log index
     * @return Whether or not the log has an entry (on given term) with the given log index
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
     * @param newEntries
     */
    public void appendNewEntries(SortedMap<Integer, LogEntry> newEntries) {
        synchronized (entriesSync) {
            newEntries.forEach((key, log) -> entries.putIfAbsent(key, log));
            printLog();
        }
    }

    public void printLog() {
        System.out.println("---Log---------------");
        for(Map.Entry<Integer, LogEntry> entry : entries.entrySet()) {
            System.out.println("" + entry.getKey() + ": [Term " + entry.getValue().getTerm() + "] " + entry.getValue().getVarName() + " ← " + entry.getValue().getValue());
        }
        System.out.println("---------------------");
    }

    /**
     * @see it.polimi.server.state.State#getLastLogIndex() getLastLogIndex
     * @throws NoSuchElementException
     */
    public int getLastIndex() throws NoSuchElementException{
        synchronized (entriesSync) {
            return entries.lastKey();
        }
    }

    public LogEntry getEntry(Integer index) throws NoSuchElementException {
        return index == null ? null : entries.get(index);
    }

    public Integer getIndexBefore(Integer index) {
        if(index == null) {
            return null;
        }
        try {
            synchronized (entriesSync) {
                return entries.headMap(index).lastKey();
            }
        } catch (NoSuchElementException e) {
            return null;
        }
    }

    public SortedMap<Integer, LogEntry> getEntriesSince(Integer next) {
        if(next == null) {
            return null;
        }
        synchronized (entriesSync) {
            return entries.tailMap(next);
        }
    }

    public void addEntry(int term, String variable, Integer value, Integer requestNumber, Integer clientRequestNumber) {
        synchronized (entriesSync) {
            entries.put(nextKey, new LogEntry(term, variable, value, requestNumber, clientRequestNumber, nextKey));
            nextKey++;
            printLog();
        }
    }
    
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
        clearUpToIndex(commitIndex);
    }

    /**
     * Write the last commit on file
     * @param toWrite What to write
     * @throws IOException When there's an error in input output functions
     */
    private void writeSnapshot(String toWrite) throws IOException {
        Files.write(storage, toWrite.getBytes());
    }
    
    private void clearUpToIndex(Integer lastIncludedIndex) {
        synchronized (entriesSync) {
            entries = entries.tailMap(lastIncludedIndex);
            entries.remove(lastIncludedIndex);
        }
    }
}
