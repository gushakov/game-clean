package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to take a specific thing off the ground, designated <em>by description</em> —
 * produced by {@code take <target>} and {@code get <target>}. The {@code target} is the free-text remainder of
 * the command line (a possibly multi-word fragment like "rusty sword"); the console hands it inward as a
 * primitive and the {@code Take} use case does the matching. Mirrors {@link ExamineCommand}; the disambiguation
 * follow-up is a bare {@link SelectCommand}, routed to {@code take} by the armed selection kind.
 */
@Value
public class TakeCommand implements Command {
    String target;
}
