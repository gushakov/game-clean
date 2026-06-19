package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to end the session. The console dispatches it to the {@code SuspendGame} use
 * case — which banks this session's elapsed time into the clock (Model B "pause on quit") — and then breaks
 * its read loop. So it both triggers a use case and controls the loop.
 */
@Value
public class QuitCommand implements Command {
}
