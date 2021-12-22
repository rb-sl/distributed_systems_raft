package it.polimi.networking.messages;

import lombok.Getter;

@Getter
public class Result extends Message {
    private int term;
    private boolean success;

    public Result(Integer requestNumber, int term, boolean success) {
        super.messageType = Type.Result;
        super.internalRequestNumber = requestNumber;
        this.term = term;
        this.success = success;
    }
}
