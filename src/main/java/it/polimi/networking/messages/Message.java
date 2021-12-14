package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import lombok.Getter;

import java.io.Serializable;

@Getter
public abstract class Message implements Serializable {
    public enum Type {
        AppendEntry, RequestVote, Result, StateTransition, StartElection, WriteRequest, ReadRequest, UpdateIndex
    }

    protected Type messageType;
    protected RemoteServerInterface origin;
    protected Integer requestNumber;

    @Override
    public String toString() {
        return "Message{" +
                "messageType=" + messageType +
                ", origin=" + origin +
                ", requestNumber=" + requestNumber +
                '}';
    }
}
