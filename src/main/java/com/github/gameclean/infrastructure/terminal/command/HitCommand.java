package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to strike a specific NPC, designated <em>by description</em> — produced by
 * {@code hit <target>}. The {@code target} is the free-text remainder of the command line (a possibly
 * multi-word fragment like "hooded wanderer"); the console hands it inward as a primitive and the {@code Hit}
 * use case does the matching. Mirrors {@link TakeCommand}; the disambiguation follow-up is a bare
 * {@link SelectCommand}, routed to {@code hit} by the armed selection kind.
 */
@Value
public class HitCommand implements Command {
    String target;
}
