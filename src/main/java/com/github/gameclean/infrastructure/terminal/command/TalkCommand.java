package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to talk to a specific NPC, designated <em>by description</em> — produced by
 * {@code talk <target>} / {@code speak <target>} (an idiomatic leading {@code to} / {@code with} already
 * stripped by the parser). The {@code target} is the free-text remainder of the command line (a possibly
 * multi-word fragment like "hooded wanderer"); the console hands it inward as a primitive and the
 * {@code TalkToNpc} use case does the matching. Mirrors {@link HitCommand}; the disambiguation follow-up is a
 * bare {@link SelectCommand}, routed to {@code talk} by the armed selection kind.
 */
@Value
public class TalkCommand implements Command {
    String target;
}
