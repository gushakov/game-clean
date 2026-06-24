package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player wants to examine a specific thing, designated <em>by description</em> — produced by
 * {@code look <target>} and {@code examine <target>}. The {@code target} is the free-text remainder of the
 * command line (a possibly multi-word fragment like "rusty sword"); the console hands it inward as a primitive
 * and the {@code Examine} use case does the matching.
 */
@Value
public class ExamineCommand implements Command {
    String target;
}
