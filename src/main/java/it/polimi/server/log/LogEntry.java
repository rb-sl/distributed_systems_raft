package it.polimi.server.log;

import lombok.Getter;

public class LogEntry {
    @Getter
    private final int term;
    @Getter
    private final String varName;
    @Getter
    private final Integer value;

    public LogEntry(int term, String varName, Integer value) {
        this.term = term;
        this.varName = varName;
        this.value = value;
    }
}
