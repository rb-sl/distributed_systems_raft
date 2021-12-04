package it.polimi.networking.messages;

import java.io.Serializable;

public record Result(int term, boolean success) implements Serializable {

}
