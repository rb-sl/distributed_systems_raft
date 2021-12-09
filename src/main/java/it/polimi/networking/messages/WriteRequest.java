package it.polimi.networking.messages;

import it.polimi.networking.RemoteServerInterface;
import lombok.Getter;

@Getter
public class WriteRequest extends Message {
    String variable;
    Integer value;

    public WriteRequest(Integer requestNumber, String variable, Integer value) {
        super.messageType = Type.WriteRequest;
        super.requestNumber = requestNumber;

        this.variable = variable;
        this.value = value;
    }
}
