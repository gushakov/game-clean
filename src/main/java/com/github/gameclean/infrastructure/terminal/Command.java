package com.github.gameclean.infrastructure.terminal;

/**
 * A parsed player command — the driving adapter's internal representation of user <em>intent</em>,
 * the output of {@link CommandParser}. It is deliberately an <strong>infrastructure-local</strong>
 * type: it never crosses into the core. The console controller unpacks it into a use-case call,
 * carrying primitives inward (see {@code LookInputPort}). Keeping syntax here is what lets the parser
 * be swapped (a richer grammar, a different transport) without the core ever knowing command syntax
 * existed.
 *
 * <p>Implementations are the small closed set of intents the parser can produce: {@link LookCommand},
 * {@link QuitCommand}, {@link UnknownCommand}.
 */
public interface Command {
}
