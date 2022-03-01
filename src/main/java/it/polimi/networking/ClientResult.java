package it.polimi.networking;

import lombok.Getter;

/**
 * Result sent to clients
 */
@Getter
public class ClientResult {
    public enum Status {
        OK, NOTLEADER
    }

    /**
     * The original request's number
     */
    private final Integer clientRequestNumber;
    /**
     * The request result
     */
    private final Integer result;
    /**
     * Message status
     */
    private final Status status;

    public ClientResult(Integer clientRequestNumber, Integer result, Status status) {
        this.clientRequestNumber = clientRequestNumber;
        this.result = result;
        this.status = status;
    }
}
