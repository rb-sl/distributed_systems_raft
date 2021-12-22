package it.polimi.networking;

import lombok.Getter;

@Getter
public class ClientResult {
    public enum Status {
        OK, NOTLEADER
    }
    Integer clientRequestNumber;
    Integer result;
    Status status;

    public ClientResult(Integer clientRequestNumber, Integer result, Status status) {
        this.clientRequestNumber = clientRequestNumber;
        this.result = result;
        this.status = status;
    }
}
