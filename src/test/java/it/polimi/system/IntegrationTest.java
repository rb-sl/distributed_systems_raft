package it.polimi.system;

import it.polimi.client.admin.Admin;
import it.polimi.client.user.User;
import it.polimi.server.Server;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it.polimi.utilities.ProcessStarter.startServerProcess;
import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {    
    public void testWait(Integer ms) {
        try {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(ms);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    } 
    
    @Test
    void processTest() {
        Server s = new Server("localtest_server1");
        Thread thread = new Thread(s::start);
        thread.setDaemon(true);
        thread.start();

        Process process2;
        Process process3;
        try {
            process2 = startServerProcess("localtest_server2", 1, true);
            process3 = startServerProcess("localtest_server3", 2, true);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            fail();
            return;
        }

        testWait(4000);

        assertEquals("localtest_server1", s.getId());
        
        process2.destroy();
        process3.destroy();
    }
    
    @Test
    void commandTest() {
        Admin admin = new Admin("localtest_admin1");
        
        assertDoesNotThrow(() -> admin.startServer("localtest_server1"));
        assertDoesNotThrow(() -> admin.startServer("localtest_server2"));
        assertDoesNotThrow(() -> admin.startServer("localtest_server3"));

        testWait(4000);

        assertDoesNotThrow(() -> admin.killServer("localtest_server2"));
        assertDoesNotThrow(() -> admin.killServer("localtest_server3"));
        assertDoesNotThrow(() -> admin.killServer("localtest_server1"));
    }
    
    void clientWrite(User user) {
        for(int i = 0; i < 200; i++) {
            user.writeToCluster("x", i);
        }
    }
    
    @Test
    void singleClientInteractionTest() {
        Server server1 = new Server("localtest_server1");
        Thread thread = new Thread(server1::start);
        thread.setDaemon(true);
        thread.start();

        Process process2;
        Process process3;
        try {
            process2 = startServerProcess("localtest_server2", 1, true);
            process3 = startServerProcess("localtest_server3", 2, true);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            fail();
            return;
        }

        // Server startup
        testWait(2000);

        User user = new User("localtest_user1");
        
        Integer x = 0;
        clientWrite(user);
        x = user.readFromCluster("x");
        
        assertEquals(x, 199);

        testWait(1000);

        user.writeToCluster("x", 3);
        user.writeToCluster("x", 4);

        x = user.readFromCluster("x");

        assertEquals(x, 4);
        
        // Waiting a bit before closing
        testWait(1000);

        assertEquals("localtest_server1", server1.getId());

        process2.destroy();
        process3.destroy();
    }

    @Test
    void multiClientInteractionTest() {
        Server server1 = new Server("localtest_server1");
        Thread thread = new Thread(server1::start);
        thread.setDaemon(true);
        thread.start();

        Process process2;
        Process process3;
        try {
            process2 = startServerProcess("localtest_server2", 1, true);
            process3 = startServerProcess("localtest_server3", 2, true);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            fail();
            return;
        }

        // Server startup
        testWait(2000);

        User user1 = new User("localtest_user1");
        User user2 = new User("localtest_user2");

        Integer x1 = 0;
        Integer x2 = 0;
        Thread thread1 = new Thread(() -> clientWrite(user1));
        thread1.start();
        
        clientWrite(user2);

        x1 = user1.readFromCluster("x");
        x2 = user1.readFromCluster("x");
        
        // Waiting a bit before closing
        testWait(2000);

        assertEquals(x1, 199);

        assertEquals(x1, x2);

        assertEquals("localtest_server1", server1.getId());

        process2.destroy();
        process3.destroy();
    }

    @Test
    void reconfigureTest() {
        Admin a = new Admin("reconfigureTest_admin");
        a.startServer("reconfigureTest_server1");
        a.startServer("reconfigureTest_server2");
        a.startServer("reconfigureTest_server3");
        a.startServer("reconfigureTest_server4");

        // Waiting for servers to start
        testWait(5000);
        
        a.sendConfiguration("reconfigureTest_conf234");

        testWait(5000);

        a.sendConfiguration("reconfigureTest_conf123");

        testWait(5000);

//        a.killServer("reconfigureTest_server1");
//        a.killServer("reconfigureTest_server2");
//        a.killServer("reconfigureTest_server3");
//        a.killServer("reconfigureTest_server4");
    }
}
