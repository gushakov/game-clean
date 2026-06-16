package com.github.gameclean.infrastructure.terminal;

import lombok.Value;

/**
 * Intent: the player wants to end the session. Loop control, not a use case — the console breaks its
 * read loop on it.
 */
@Value
public class QuitCommand implements Command {
}
