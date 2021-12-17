package it.polimi.networking;

import lombok.Getter;

@Getter
public class ClientResult {
    public enum Status {
        OK, NOTLEADER
    }
    Integer result;
    Status status;

    public ClientResult(Integer result, Status status) {
        this.result = result;
        this.status = status;
    }
}
