package it.polimi.networking.messages;

import lombok.Getter;

/**
 * Message containing RPC results
 */
@Getter
public class Result extends Message {
    /**
     * currentTerm, for counterpart to update itself
     */
    private final int term;
    /**
     * Success, meaning is relative to the original message
     */
    private final boolean success;

    public Result(Integer requestNumber, int term, boolean success) {
        super.messageType = Type.Result;
        super.internalRequestNumber = requestNumber;
        this.term = term;
        this.success = success;
    }
}
