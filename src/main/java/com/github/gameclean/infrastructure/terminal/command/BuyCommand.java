package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to buy a ware offered by an NPC in their scene, designated <em>by description</em>
 * — produced by {@code buy <target>} / {@code rent <target>} (the two verbs are plain synonyms; a leading
 * article is already stripped by the parser). The {@code target} is the free-text remainder of the command
 * line (a possibly multi-word fragment like "room"); the console hands it inward as a primitive and the
 * {@code BuyFromNpc} use case does the matching. Mirrors {@link TalkCommand}; the disambiguation follow-up is
 * a bare {@link SelectCommand}, routed to {@code buy} by the armed selection kind.
 */
@Value
public class BuyCommand implements Command {
    String target;
}
