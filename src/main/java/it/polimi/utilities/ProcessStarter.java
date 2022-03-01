package it.polimi.utilities;

import it.polimi.server.AppServer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Class used by the client to start a new server
 */
public class ProcessStarter {
    public static Process startServerProcess(String serverName, Integer tabs, boolean inheritIO) throws IOException, InterruptedException {
        String javaHome = System.getProperty("java.home");
        String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        String className = AppServer.class.getName();

        List<String> command = new ArrayList<>();
        command.add(javaBin);
        command.add("-cp");
        command.add(classpath);
        command.add(className);
        command.add(serverName);
        command.add(tabs.toString());

        ProcessBuilder builder;
        if(inheritIO) {
            builder = new ProcessBuilder(command);
            return builder.inheritIO().start();
        }
        else {
            List<String> cmdCommand = new ArrayList<>();
            cmdCommand.add("cmd.exe");
            cmdCommand.add("/C");
            cmdCommand.add("start");
            cmdCommand.addAll(command);
            builder = new ProcessBuilder(cmdCommand);
            return builder.start();
        }
    }
}
