package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to move through a named exit of their current scene. Unlike {@link LookCommand}
 * it carries an argument — the exit name — which the console passes inward to
 * {@code MoveInputPort.playerMovesThrough(exitName)} as a primitive.
 */
@Value
public class MoveCommand implements Command {
    String exitName;
}
