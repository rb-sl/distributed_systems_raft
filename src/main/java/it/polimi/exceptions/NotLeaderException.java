package it.polimi.exceptions;

import it.polimi.networking.RemoteServerInterface;
import lombok.Getter;

@Getter
public class NotLeaderException extends Exception {
    private final RemoteServerInterface leader;

    public NotLeaderException(String message, RemoteServerInterface leader) {
        super(message);
        this.leader = leader;
    }
}
