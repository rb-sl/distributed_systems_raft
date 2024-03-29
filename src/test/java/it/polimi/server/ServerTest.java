package it.polimi.server;

import it.polimi.networking.messages.AppendEntries;
import it.polimi.server.log.LogEntry;
import org.junit.jupiter.api.Test;

import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class ServerTest {
    @Test
    void appendEntriesTest() {
        Server s = new Server();
        Thread thread = new Thread(s::start);
        thread.setDaemon(true);
        thread.start();

        try {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(4000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        SortedMap<Integer, LogEntry> ent = new TreeMap<>();
        ent.put(0, new LogEntry(0, "null", null, 0, 0, 0));
        ent.put(1, new LogEntry(0, "x", 2, 0, 1, 1));
        ent.put(2, new LogEntry(0, "x", 3, 0, 2, 2));
        ent.put(3, new LogEntry(0, "x", 4, 0, 3, 3));

        s.appendEntries(new AppendEntries(0, null, 0, "1", null, null, ent, null));

        s.getState().getLogger().printLog();

        assertEquals(ent.toString(), s.getState().getLogger().getEntries().toString());

        SortedMap<Integer, LogEntry> ent2 = new TreeMap<>();
        ent2.put(2, new LogEntry(1, "x", 4, 0, 4, 2));

        s.appendEntries(new AppendEntries(1, null, 0, "1", 1, 0, ent2, null));

        s.getState().getLogger().printLog();
    }
}
