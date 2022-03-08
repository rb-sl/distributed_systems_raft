package it.polimi.networking.messages;

import it.polimi.server.ServerConfiguration;
import lombok.Getter;

import java.util.List;

/**
 * Message sent by clients to read a variable
 */
@Getter
public class ChangeConfiguration extends Message {
    /**
     * Old server configuration
     */
    private final ServerConfiguration configuration;

    public ChangeConfiguration(ServerConfiguration configuration) {
        super.messageType = Type.ChangeConfiguration;

        this.configuration = configuration;
    }
}
