package it.polimi.networking.messages;

import lombok.Getter;

/**
 * Message sent by clients to read a variable
 */
@Getter
public class ReadRequest extends Message {
    /**
     * Number of the request
     */
    Integer clientRequestNumber;
    /**
     * Requested variable
     */
    String variable;

    public ReadRequest(Integer requestNumber, Integer clientRequestNumber, String variable) {
        super.messageType = Message.Type.ReadRequest;
        super.internalRequestNumber = requestNumber;

        this.clientRequestNumber = clientRequestNumber;
        this.variable = variable;
    }
}
