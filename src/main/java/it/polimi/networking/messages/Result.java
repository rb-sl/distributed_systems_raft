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

    /**
     * Id of the source of the result
     */
    private final String originId;

    /**
     * Type of the original message
     */
    private final Type answerTo;

    public Result(String originId, Message.Type answerTo, Integer requestNumber, int term, boolean success) {
        super.messageType = Type.Result;
        super.internalRequestNumber = requestNumber;
        
        this.originId = originId;        
        this.term = term;
        this.success = success;
        this.answerTo = answerTo;
    }
}
