package it.polimi.networking.messages;

import lombok.Getter;

@Getter
public class ReadRequest extends Message {
    String variable;

    public ReadRequest(Integer requestNumber, String variable) {
        super.messageType = Message.Type.ReadRequest;
        super.requestNumber = requestNumber;

        this.variable = variable;
    }
}
