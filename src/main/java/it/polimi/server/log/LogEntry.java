package it.polimi.server.log;

import lombok.Getter;

import java.io.Serializable;

@Getter
public class LogEntry implements Serializable {
    private final int term;
    private final String varName;
    private final Integer value;

    /**
     * Added to be able to answer to the right client when complete
     */
    private final Integer clientRequestNumber;

    public LogEntry(int term, String varName, Integer value, Integer clientRequestNumber) {
        this.term = term;
        this.varName = varName;
        this.value = value;
        this.clientRequestNumber = clientRequestNumber;
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
