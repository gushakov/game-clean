package com.github.gameclean.infrastructure.terminal.command;

/**
 * A parsed player command — the driving adapter's internal representation of user <em>intent</em>,
 * the output of {@link CommandParser}. It is deliberately an <strong>infrastructure-local</strong>
 * type: it never crosses into the core. The console controller unpacks it into a use-case call,
 * passing any arguments inward as primitives (a marker like {@link LookCommand} carries none). Keeping
 * syntax here is what lets the parser be swapped (a richer grammar, a different transport) without the
 * core ever knowing command syntax existed.
 *
 * <p>Implementations are the small closed set of intents the parser can produce: {@link LookCommand},
 * {@link ExamineCommand}, {@link SelectCommand}, {@link MoveCommand}, {@link TimeCommand}, {@link QuitCommand},
 * {@link UnknownCommand}. The {@code sealed} clause makes that closure explicit, so the console controller's
 * pattern-matching {@code switch} over a command is <em>exhaustive</em> — adding a new intent is a compile
 * error until every dispatch handles it, rather than silently falling through.
 */
public sealed interface Command
        permits LookCommand, ExamineCommand, SelectCommand, MoveCommand, TimeCommand, QuitCommand, UnknownCommand {
}
