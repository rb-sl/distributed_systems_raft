package it.polimi.system;

import it.polimi.client.admin.AdminConsole;
import it.polimi.server.Server;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import static it.polimi.utilities.ProcessStarter.startServerProcess;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.fail;

public class IntegrationTest {
    private static Registry localRegistry;
    private static final Integer localPort = 1099;
    
    @BeforeAll
    static void startUp() {
        try {        
            localRegistry = LocateRegistry.createRegistry(localPort);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
    
    @AfterEach
    void removeServers() {
        try {
            for (String name : localRegistry.list()) {
                localRegistry.unbind(name);
            }
        } catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }
    }
    
    @Test
    void testTest() throws InterruptedException {
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
}
