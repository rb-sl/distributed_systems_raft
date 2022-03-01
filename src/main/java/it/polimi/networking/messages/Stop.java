package it.polimi.networking.messages;

import lombok.Getter;

/**
 * Internal message to signal a shut down order from a client
 */
@Getter
public class Stop extends Message {
    public Stop() {
        super.messageType = Type.Stop;
    }
}
