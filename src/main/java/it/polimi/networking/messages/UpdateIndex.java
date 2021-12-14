package it.polimi.networking.messages;

import lombok.Getter;

@Getter
public class UpdateIndex extends Message {
    Integer commitIndex;

    public UpdateIndex(Integer commitIndex) {
        super.messageType = Type.UpdateIndex;
        this.commitIndex = commitIndex;
    }
}
