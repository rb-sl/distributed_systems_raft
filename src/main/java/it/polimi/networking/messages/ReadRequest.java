package it.polimi.networking.messages;

import lombok.Getter;

@Getter
public class ReadRequest extends Message {
    Integer clientRequestNumber;
    String variable;

    public ReadRequest(Integer requestNumber, Integer clientRequestNumber, String variable) {
        super.messageType = Message.Type.ReadRequest;
        super.internalRequestNumber = requestNumber;

        this.clientRequestNumber = clientRequestNumber;
        this.variable = variable;
    }
}
