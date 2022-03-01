package it.polimi.networking.messages;

import it.polimi.server.state.State;
import lombok.Getter;

/**
 * Internal message to signal a change of state
 */
@Getter
public class StateTransition extends Message {
    /**
     * New state
     */
    private final State.Role state;

    public StateTransition(State.Role state) {
        super.messageType = Type.StateTransition;
        
        this.state = state;
    }
}
