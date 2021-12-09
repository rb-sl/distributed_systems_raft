package it.polimi.server.log;

import lombok.Getter;

import java.io.Serializable;

public class LogEntry implements Serializable {
    @Getter
    public final int term;
    @Getter
    public final String varName;
    @Getter
    public final Integer value;

    public LogEntry(int term, String varName, Integer value) {
        this.term = term;
        this.varName = varName;
        this.value = value;
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
