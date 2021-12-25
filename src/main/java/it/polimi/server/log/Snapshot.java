package it.polimi.server.log;

import lombok.Getter;

import java.util.Map;

@Getter
public class Snapshot {
    private Map<String, Integer> variables;
    private Integer lastIncludedIndex;
    private Integer lastIncludedTerm;

    public Snapshot(Map<String, Integer> variables, Integer lastIncludedIndex, Integer lastIncludedTerm) {
        this.variables = variables;
        this.lastIncludedIndex = lastIncludedIndex;
        this.lastIncludedTerm = lastIncludedTerm;
    }
}
