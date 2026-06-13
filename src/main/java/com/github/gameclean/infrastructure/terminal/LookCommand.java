package com.github.gameclean.infrastructure.terminal;

import lombok.Value;

/**
 * Intent: the player wants to look around their current scene. Carries no argument this round
 * ({@code look <target>} is deferred), so it is a pure marker the console maps to
 * {@code LookInputPort.look(playerId)}.
 */
@Value
public class LookCommand implements Command {
}
