package it.polimi.networking.messages;

import lombok.Getter;

@Getter
public class WriteRequest extends Message {
    Integer clientRequestNumber;
    String variable;
    Integer value;

    public WriteRequest(Integer requestNumber, Integer clientRequestNumber, String variable, Integer value) {
        super.messageType = Type.WriteRequest;
        super.internalRequestNumber = requestNumber;

        this.clientRequestNumber = clientRequestNumber;
        this.variable = variable;
        this.value = value;
    }
}
