package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to put down a specific thing they are carrying, designated <em>by description</em> —
 * produced by {@code drop <target>} and {@code put <target>}. The {@code target} is the free-text remainder of
 * the command line (a possibly multi-word fragment like "rusty sword"); the console hands it inward as a
 * primitive and the {@code Drop} use case does the matching. Mirrors {@link TakeCommand}; the disambiguation
 * follow-up is a bare {@link SelectCommand}, routed to {@code drop} by the armed selection kind.
 */
@Value
public class DropCommand implements Command {
    String target;
}
