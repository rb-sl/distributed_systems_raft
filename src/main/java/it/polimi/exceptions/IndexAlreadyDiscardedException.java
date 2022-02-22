package it.polimi.exceptions;

import lombok.Getter;

@Getter
public class IndexAlreadyDiscardedException extends Exception {
    private final Integer index;

    public IndexAlreadyDiscardedException(String message, Integer index) {
        super(message);
        this.index = index;
    }
}
