package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;

/**
 * Intent: the player typed a bare number — a selection from a menu the system last offered (the disambiguation
 * extension of {@code examine}). Whether the number means anything depends on context the parser does not hold:
 * the console resolves the {@code ordinal} against the remembered offer ({@code AffordanceContext}). The parser
 * produces this for any single positive-integer token; a stray number with nothing pending is reported by the
 * console, not here — the same humble division of labour as {@link UnknownCommand}.
 */
@Value
public class SelectCommand implements Command {
    int ordinal;
}
