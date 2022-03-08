package it.polimi.server.log;

import com.google.gson.Gson;
import it.polimi.exceptions.IndexAlreadyDiscardedException;
import it.polimi.server.Server;
import it.polimi.server.state.State;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

class LoggerTest {
    private static Logger logger;

    static void resetLast() {
        Logger.lastSnapshot = null;
    }
    
    @BeforeEach
    void init() {
        logger = new Logger(mock(Server.class));
        resetLast();
    }
    
    @Test
    void testTermAtPosition(){
        // Assert that a null position leads to a null term
        assertNull(logger.termAtPosition(null));
        
        // Assert that a empty entries map returns null
        assertNull(logger.termAtPosition(1));
        
        // TODO: test using snapshot
    }
    
    @Test
    void testContainConflict() {
        logger.addEntry(1, "x", 1, 1, 1);
        assertTrue(logger.containConflict(0, 2));
        assertFalse(logger.containConflict(0, 1));
    }
    
    @Test
    void testDeleteFrom() {
        SortedMap<Integer, LogEntry> expected = new TreeMap<>();
        SortedMap<Integer, LogEntry> initial = new TreeMap<>();
        
        LogEntry e1 = new LogEntry(1, "x", 1, 1, 1, 1);
        LogEntry e2 = new LogEntry(1, "x", 1, 1, 1, 2);
        LogEntry e3 = new LogEntry(1, "x", 1, 1, 1, 3);

        initial.put(1, e1);
        initial.put(2, e2);
        initial.put(3, e3);
        expected.put(1, e1);
        
        logger.appendNewEntries(initial);
        logger.deleteFrom(2);
        
        assertEquals(expected, logger.getEntries());
    }
    
    @Test
    void testAppendNewEntries() {
        assertTrue(logger.getEntries().isEmpty());
        
        LogEntry newEntry = new LogEntry(1, "x", 1, 1, 1, 1);
        SortedMap<Integer, LogEntry> newEntries = new TreeMap<>();
        newEntries.put(1, newEntry);
        logger.appendNewEntries(newEntries);
        
        assertEquals(newEntries, logger.getEntries());

        LogEntry newEntry2 = new LogEntry(2, "x", 1, 1, 1, 1);
        newEntries.put(2, newEntry2);
        logger.appendNewEntries(newEntries);
        
        assertEquals(newEntries, logger.getEntries());
    }
    
    @Test
    void testGetLastIndex() {
        assertNull(logger.getLastIndex());
        
        SortedMap<Integer, LogEntry> initial = new TreeMap<>();

        LogEntry e1 = new LogEntry(1, "x", 1, 1, 1, 1);
        LogEntry e2 = new LogEntry(1, "x", 1, 1, 1, 2);
        LogEntry e3 = new LogEntry(1, "x", 1, 1, 1, 3);

        initial.put(1, e1);
        initial.put(2, e2);
        initial.put(3, e3);
        
        logger.appendNewEntries(initial);
        assertEquals(3, logger.getLastIndex());
    }
    
    @Test
    void testGetIndexBefore() {
        try {
            assertNull(logger.getIndexBefore(null));
            assertThrows(IndexAlreadyDiscardedException.class, () -> logger.getIndexBefore(1));

            SortedMap<Integer, LogEntry> initial = new TreeMap<>();
            LogEntry e1 = new LogEntry(1, "x", 1, 1, 1, 1);
            initial.put(1, e1);
            logger.appendNewEntries(initial);

            assertNull(logger.getIndexBefore(1));

            initial = new TreeMap<>();
            LogEntry e2 = new LogEntry(1, "x", 1, 1, 1, 2);
            initial.put(2, e2);
            logger.appendNewEntries(initial);
            
            assertEquals(1, logger.getIndexBefore(2));
        } catch(IndexAlreadyDiscardedException e) {
            fail();
        }
    }
    
    @Test
    void testGetEntriesSince() {
        try {
            assertNull(logger.getEntriesSince(null));
            assertThrows(IndexAlreadyDiscardedException.class, () -> logger.getEntriesSince(1));

            SortedMap<Integer, LogEntry> initial = new TreeMap<>();
            LogEntry e1 = new LogEntry(1, "x", 1, 1, 1, 1);
            LogEntry e2 = new LogEntry(1, "x", 1, 1, 1, 2);
            LogEntry e3 = new LogEntry(1, "x", 1, 1, 1, 3);
            initial.put(1, e1);
            initial.put(2, e2);
            initial.put(3, e3);
            logger.appendNewEntries(initial);
            initial.remove(1);
            
            assertEquals(initial, logger.getEntriesSince(2));
        } catch (IndexAlreadyDiscardedException e) {
            fail();
        }
    }
    
    @Test
    void testClearUpToIndex() {
        SortedMap<Integer, LogEntry> initial = new TreeMap<>();
        LogEntry e1 = new LogEntry(1, "x", 1, 1, 1, 1);
        LogEntry e2 = new LogEntry(1, "x", 1, 1, 1, 2);
        LogEntry e3 = new LogEntry(1, "x", 1, 1, 1, 3);
        initial.put(1, e1);
        initial.put(2, e2);
        initial.put(3, e3);
        logger.appendNewEntries(initial);
        logger.clearUpToIndex(1, true);
        initial.remove(1);
        assertEquals(initial, logger.getEntries());
        
        logger.clearUpToIndex(2, false);
        assertEquals(initial, logger.getEntries());
    }
    
    @Test
    void testTakeSnapshot() {
        Gson gson = new Gson();
        Server server = mock(Server.class);
        State state = mock(State.class);
        when(server.getState()).thenReturn(state);
        when(state.getCommitIndex()).thenReturn(1);

        HashMap<String, Integer> vars = new HashMap<>();
        vars.put("x", 1);
        when(state.getVariables()).thenReturn(vars);

        File newFile = new File("./snapshot_test.json");
        boolean success = false;
        try {
            success = newFile.createNewFile();
        } catch (IOException e) {
            fail("File not created");
        }
        Logger l = new Logger(server, Paths.get("./snapshot_test.json"));

        SortedMap<Integer, LogEntry> initial = new TreeMap<>();
        LogEntry e1 = new LogEntry(1, "x", 1, 1, 1, 1);
        LogEntry e2 = new LogEntry(1, "x", 1, 1, 1, 2);
        LogEntry e3 = new LogEntry(1, "x", 1, 1, 1, 3);
        initial.put(1, e1);
        initial.put(2, e2);
        initial.put(3, e3);
        l.appendNewEntries(initial);

        l.takeSnapshot();

        try {
            String line = Files.readAllLines(l.getStorage()).get(0);
            Snapshot fromjson = gson.fromJson(line, Snapshot.class);
            assertEquals(new Snapshot(vars, 1, 1), fromjson);
        } catch (IOException e) {
            e.printStackTrace();
        }

        newFile.delete();
    }
}
