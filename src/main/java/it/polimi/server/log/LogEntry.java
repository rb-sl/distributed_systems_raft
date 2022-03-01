package it.polimi.server.log;

import lombok.Getter;

import java.io.Serializable;

/**
 * Entry for a server log
 */
@Getter
public class LogEntry implements Serializable {
    /**
     * The entry's term
     */
    private final int term;
    /**
     * The subject variable
     */
    private final String varName;
    /**
     * The written value
     */
    private final Integer value;
    /**
     * The position in the log
     */
    private final Integer index;

    /**
     * Added to be able to answer to the right request when complete
     */
    private final Integer internalRequestNumber;
    /**
     * Added to be able to answer to the right client when complete
     */
    private final Integer clientRequestNumber;

    public LogEntry(int term, String varName, Integer value, Integer internalRequestNumber, Integer clientRequestNumber, Integer index) {
        this.term = term;
        this.varName = varName;
        this.value = value;
        this.internalRequestNumber = internalRequestNumber;
        this.clientRequestNumber = clientRequestNumber;
        this.index = index;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "term=" + term +
                ", varName='" + varName + '\'' +
                ", value=" + value +
                '}';
    }
}
