package it.polimi.server.log;

import lombok.Getter;

import java.util.Map;
import java.util.Objects;

/**
 * Class used to import or export snapshots to json files
 */
@Getter
public class Snapshot {
    /**
     * The size of chunks of data sent in snapshots
     */
    public static final int CHUNK_DIMENSION = 42;

    /**
     * The variable state
     */
    private final Map<String, Integer> variables;
    /**
     * The snapshot replaces all entries up through and including this index
     */
    private final Integer lastIncludedIndex;
    /**
     * Term of lastIncludedIndex
     */
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
