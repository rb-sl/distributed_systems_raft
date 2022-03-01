package it.polimi.networking.messages;

import lombok.Getter;

/**
 * Message sent by clients to write to a variable
 */
@Getter
public class WriteRequest extends Message {
    /**
     * Number of the request
     */
    private final Integer clientRequestNumber;
    /**
     * Variable to write
     */
    private final String variable;
    /**
     * Value to write
     */
    private final Integer value;

    public WriteRequest(Integer requestNumber, Integer clientRequestNumber, String variable, Integer value) {
        super.messageType = Type.WriteRequest;
        super.internalRequestNumber = requestNumber;

        this.clientRequestNumber = clientRequestNumber;
        this.variable = variable;
        this.value = value;
    }
}
