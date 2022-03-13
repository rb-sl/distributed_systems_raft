package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import lombok.Getter;

/**
 * Internal message to signal a change of state
 */
@Getter
public class ServerAvailable extends Message {
    /**
     * Id of the available server
     */
    private final String serverName;
    /**
     * Available server's interface
     */
    private final RemoteServerInterface serverInterface;

    public ServerAvailable(String serverName, RemoteServerInterface serverInterface) {
        super.messageType = Type.ServerAvailable;
        
        this.serverName = serverName;
        this.serverInterface = serverInterface;
    }
}
