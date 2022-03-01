package it.polimi.networking.messages;

import lombok.Getter;

/**
 * Internal message to update the server index
 */
@Getter
public class UpdateIndex extends Message {
    /**
     * The new commit index
     */
    private final Integer commitIndex;

    public UpdateIndex(Integer commitIndex) {
        super.messageType = Type.UpdateIndex;
        
        this.commitIndex = commitIndex;
    }
}
