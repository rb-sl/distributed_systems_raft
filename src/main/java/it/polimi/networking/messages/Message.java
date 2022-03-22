package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import lombok.Getter;

import java.io.Serializable;

/**
 * Superclass for messages
 */
@Getter
public abstract class Message implements Serializable {
    /**
     * Message types
     */
    public enum Type {
        AppendEntry, RequestVote, Result, StateTransition, StartElection, WriteRequest,
        ReadRequest, UpdateIndex, InstallSnapshot, Stop, ChangeConfiguration, ServerAvailable
    }

    /**
     * The message type
     */
    protected Type messageType;
    /**
     * Server sending the message
     */
    protected RemoteServerInterface origin;
    /**
     * Receipt for the message used in the server
     */
    protected Integer internalRequestNumber;

    @Override
    public String toString() {
        return "Message{" +
                "messageType=" + messageType +
                ", origin=" + origin +
                ", requestNumber=" + internalRequestNumber +
                '}';
    }
}
