package it.polimi.exceptions;

import lombok.Getter;

@Getter
public class ServerStoppedException extends Exception {
    public ServerStoppedException() {
        super();
    }
}
