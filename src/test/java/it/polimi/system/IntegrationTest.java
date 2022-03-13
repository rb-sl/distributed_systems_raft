package it.polimi.system;

import it.polimi.client.admin.AdminConsole;
import it.polimi.client.user.User;
import it.polimi.exceptions.NotLeaderException;
import it.polimi.networking.RemoteServerInterface;
import it.polimi.server.Server;
import it.polimi.server.ServerConfiguration;
import it.polimi.server.log.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static it.polimi.utilities.ProcessStarter.startServerProcess;
import static org.junit.jupiter.api.Assertions.*;

public class IntegrationTest {
    private static Registry localRegistry;
    private static final Integer localPort = 1099;
    
    @BeforeAll
    static void startUp() {
//        try {        
//            localRegistry = LocateRegistry.createRegistry(localPort);
//        } catch (RemoteException e) {            
//            e.printStackTrace();
//            try {
//                localRegistry = LocateRegistry.getRegistry();
//            } catch (RemoteException remoteException) {
//                remoteException.printStackTrace();
//            }
//        }
    }
    
    @AfterEach
    void removeServers() {
//        try {
//            for (String name : localRegistry.list()) {
//                localRegistry.unbind(name);
//            }
//        } catch (RemoteException | NotBoundException e) {
//            e.printStackTrace();
//        }
    }
    
    @Test
    void testTest() {
        Server s = new Server("server1");
        Thread thread = new Thread(s::start);
        thread.setDaemon(true);
        thread.start();

        Process process2;
        Process process3;
        try {
            process2 = startServerProcess("server2", 1, true);
            process3 = startServerProcess("server3", 2, true);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            fail();
            return;
        }

        try {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(4000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals("server1", s.getId());
        
        process2.destroy();
        process3.destroy();
    }
    
    @Test
    void commandTest() {
        AdminConsole adminConsole = new AdminConsole();
        
        assertDoesNotThrow(() -> adminConsole.startServer("server1"));
        assertDoesNotThrow(() -> adminConsole.startServer("server2"));
        assertDoesNotThrow(() -> adminConsole.startServer("server3"));

        try {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(4000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertDoesNotThrow(() -> adminConsole.killServer("server2"));
        assertDoesNotThrow(() -> adminConsole.killServer("server3"));
        assertDoesNotThrow(() -> adminConsole.killServer("server1"));
    }
    
    void clientWrite(User user) {
        for(int i = 0; i < 200; i++) {
            user.writeToCluster("x", i);
        }
    }
    
    @Test
    void singleClientInteractionTest() {
        Server server1 = new Server("server1");
        Thread thread = new Thread(server1::start);
        thread.setDaemon(true);
        thread.start();

        Process process2;
        Process process3;
        try {
            process2 = startServerProcess("server2", 1, true);
            process3 = startServerProcess("server3", 2, true);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            fail();
            return;
        }

        // Server startup
        try {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(2000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        User user = new User("user1");
        
        Integer x = 0;
        clientWrite(user);
        x = user.readFromCluster("x");
        
        assertEquals(x, 199);
//        Logger logger1 = server1.getServerState().getLogger();
//        assertEquals(logger1.getEntry(logger1.getLastIndex()).getValue(), 199);

        try {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(1000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        
        user.writeToCluster("x", 3);
        user.writeToCluster("x", 4);

        x = user.readFromCluster("x");

        assertEquals(x, 4);
        
        // Waiting a bit before closing
        try {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(2000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals("server1", server1.getId());

        process2.destroy();
        process3.destroy();
    }

    @Test
    void multiClientInteractionTest() {
        Server server1 = new Server("server1");
        Thread thread = new Thread(server1::start);
        thread.setDaemon(true);
        thread.start();

        Process process2;
        Process process3;
        try {
            process2 = startServerProcess("server2", 1, true);
            process3 = startServerProcess("server3", 2, true);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            fail();
            return;
        }

        // Server startup
        try {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(2000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        User user1 = new User("user1");
        User user2 = new User("user2");

        Integer x = 0;
        Thread thread1 = new Thread(() -> clientWrite(user1));
        thread1.start();
        
        clientWrite(user2);

        x = user1.readFromCluster("x");

        assertEquals(x, 199);

        // Waiting a bit before closing
        try {
            synchronized (Thread.currentThread()) {
                Thread.currentThread().wait(2000);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertEquals("server1", server1.getId());

        process2.destroy();
        process3.destroy();
    }

    @Test
    void ttt() {
        AdminConsole a = new AdminConsole();        
        a.sendConfiguration("newConf");        
    }
}
