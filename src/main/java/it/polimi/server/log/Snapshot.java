package it.polimi.server.log;

import lombok.Getter;

import java.util.Map;
import java.util.Objects;

@Getter
public class Snapshot {
    public static final int CHUNK_DIMENSION = 42;
    
    private final Map<String, Integer> variables;
    private final Integer lastIncludedIndex;
    private final Integer lastIncludedTerm;

    public Snapshot(Map<String, Integer> variables, Integer lastIncludedIndex, Integer lastIncludedTerm) {
        this.variables = variables;
        this.lastIncludedIndex = lastIncludedIndex;
        this.lastIncludedTerm = lastIncludedTerm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Snapshot snapshot = (Snapshot) o;
        return variables.equals(snapshot.variables) 
                && Objects.equals(lastIncludedIndex, snapshot.lastIncludedIndex) 
                && Objects.equals(lastIncludedTerm, snapshot.lastIncludedTerm);
    }

    @Override
    public int hashCode() {
        return Objects.hash(variables, lastIncludedIndex, lastIncludedTerm);
    }

    @Override
    public String toString() {
        return "Snapshot{" +
                "variables=" + variables +
                ", lastIncludedIndex=" + lastIncludedIndex +
                ", lastIncludedTerm=" + lastIncludedTerm +
                '}';
    }
}
