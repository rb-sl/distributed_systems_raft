package it.polimi.system;

import it.polimi.server.Server;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it.polimi.utilities.ProcessStarter.startServerProcess;
import static org.junit.jupiter.api.Assertions.fail;

public class IntegrationTest {
    @Test
    void testTest() {      
        // todo rmi registry       
        
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
}
