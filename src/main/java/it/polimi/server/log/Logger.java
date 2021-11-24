package it.polimi.server.log;

import java.util.*;

public class Logger {
    /**
     * The map of LogEntries in function of log index
     * The map is ordered on the log index
     */
    private SortedMap<Integer, LogEntry> entries;


    public Logger() {
        this.entries = new TreeMap<>();
    }

    /**
     * Checks whether or not the log has an entry (on given term) with the given log index
     * @param term The term
     * @param position The log index
     * @return Whether or not the log has an entry (on given term) with the given log index
     */
    public Integer termAtPosition(Integer position) {
        if(entries.isEmpty() || !entries.containsKey(position)) {
            return null;
        }
        return entries.get(position).getTerm();
    }

    /**
     * Checks whether an entry is in conflict with a new one (same position, but different term)
     * @param position The position
     * @param term The term
     * @return Whether or not there is a conflict
     */
    public Boolean containConflict(Integer position, Integer term) {
        return entries.containsKey(position) && (entries.get(position).getTerm() != term);
    }

    /**
     * Deletes from log all entries with key greater or equal than logIndex
     * @param logIndex The log index
     */
    public void deleteFrom(int logIndex) {
        entries.entrySet().removeIf(entry -> entry.getKey() >= logIndex);
    }

    /**
     * Append new entries to the old ones.<\br>
     * Entries with the same location of existing ones are ignored
     * @param newEntries
     */
    public void appendNewEntries(SortedMap<Integer, LogEntry> newEntries) {
        newEntries.forEach((key, log) -> entries.putIfAbsent(key, log));
    }

    public void printLog() {
        System.out.println("------------------");
        for(Map.Entry<Integer, LogEntry> entry : entries.entrySet()) {
            System.out.println(entry.getKey() + ": term " + entry.getValue().getTerm() + ", value" + entry.getValue().getValue());
        }
        System.out.println("------------------");
    }
}
