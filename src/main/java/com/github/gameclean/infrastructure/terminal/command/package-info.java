/**
 * Command parsing — the driving adapter's <em>delivery-mechanism</em> concern (design-notes §9). Turning a
 * raw input line into a typed intent is the controller's job, kept entirely in infrastructure so command
 * syntax never crosses into the core.
 *
 * <p>The {@code sealed} {@link com.github.gameclean.infrastructure.terminal.command.Command} intent
 * hierarchy and the {@link com.github.gameclean.infrastructure.terminal.command.CommandParser} that produces
 * it (a tokenizer + verb registry) live together here. Co-locating the sealed {@code permits} set keeps the
 * closed command vocabulary in one place — and keeps {@code ConsoleSession}'s pattern-matching dispatch
 * {@code switch} exhaustive, so a new intent is a compile error until every dispatch handles it.
 */
package com.github.gameclean.infrastructure.terminal.command;
