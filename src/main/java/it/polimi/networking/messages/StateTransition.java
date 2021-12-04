package it.polimi.networking.messages;

import it.polimi.server.state.State;
import lombok.Getter;

@Getter
public class StateTransition extends Message {
    private State.Role state;

    public StateTransition(State.Role state) {
        super.messageType = Type.StateTransition;
        this.state = state;
    }
}
