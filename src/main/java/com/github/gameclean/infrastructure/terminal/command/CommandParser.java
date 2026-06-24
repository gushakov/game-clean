package com.github.gameclean.infrastructure.terminal.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Turns a raw input line into a {@link Command} intent — a tokenizer plus a small command <em>registry</em>.
 * Each verb is registered with a single factory that inspects the argument tokens and returns the command it
 * builds, or {@code null} when those arguments do not fit the verb (the line then becomes an
 * {@link UnknownCommand}). Adding a command, a synonym, or an extra arity is one {@link #register} line, which
 * is the whole point: the language is programmable from one place, with no parser-generator machinery for what
 * is still a {@code verb [words]} grammar. The seam is intentionally shaped so a richer grammar (ANTLR or
 * otherwise) could replace this class without touching the console controller or the core.
 *
 * <p>Two shapes go beyond the plain "verb + fixed arity" form, both driven by {@code examine}:
 * <ul>
 *   <li><b>A verb with several arities.</b> {@code look} alone means "look around" ({@link LookCommand}); with a
 *       target it means "examine that" ({@link ExamineCommand}). One factory branches on the argument count.</li>
 *   <li><b>The remainder as one argument.</b> {@code look <words>} / {@code examine <words>} take the whole
 *       remainder of the line as a single target ("rusty sword"), since a thing's description can be
 *       multi-word — unlike {@code move <exit>}, whose single token is an exit name.</li>
 * </ul>
 *
 * <p>A line that is a single positive integer is a {@link SelectCommand} (a menu pick) regardless of the verb
 * registry — its <em>meaning</em> depends on context the console holds, but its <em>shape</em> is recognised
 * here. Anything else that matches no verb (or whose arguments no factory accepts) becomes an
 * {@link UnknownCommand} carrying the original input; blank input yields no command at all.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
public class CommandParser {

    /** A verb's factory: build a command from the argument tokens, or return {@code null} if they do not fit. */
    @FunctionalInterface
    private interface CommandFactory {
        Command create(List<String> args);
    }

    private final Map<String, CommandFactory> registry = new HashMap<>();

    public CommandParser() {
        // 'look' alone looks around; 'look <words>' examines the described target.
        register("look", args -> args.isEmpty() ? new LookCommand() : new ExamineCommand(joinRemainder(args)));
        // 'examine'/'x' always take a target; bare 'examine' does not fit (falls through to unknown).
        register("examine", args -> args.isEmpty() ? null : new ExamineCommand(joinRemainder(args)));
        register("x", args -> args.isEmpty() ? null : new ExamineCommand(joinRemainder(args)));
        // 'move'/'go' take exactly one exit-name token.
        register("move", args -> args.size() == 1 ? new MoveCommand(args.get(0)) : null);
        register("go", args -> args.size() == 1 ? new MoveCommand(args.get(0)) : null);
        register("now", args -> args.isEmpty() ? new TimeCommand() : null);
        register("time", args -> args.isEmpty() ? new TimeCommand() : null);
        register("bye", args -> args.isEmpty() ? new QuitCommand() : null);
        register("quit", args -> args.isEmpty() ? new QuitCommand() : null);
    }

    /**
     * @return the parsed command, or empty when the line is blank (a no-op the console skips).
     */
    public Optional<Command> parse(String line) {
        if (line == null) {
            return Optional.empty();
        }
        String stripped = line.strip();
        if (stripped.isEmpty()) {
            return Optional.empty();
        }
        String[] tokens = stripped.split("\\s+");

        // A bare positive integer is a menu selection — its meaning depends on context the console holds.
        if (tokens.length == 1 && isMenuNumber(tokens[0])) {
            return Optional.of(new SelectCommand(Integer.parseInt(tokens[0])));
        }

        String verb = tokens[0].toLowerCase(Locale.ROOT);
        List<String> args = Arrays.asList(tokens).subList(1, tokens.length);

        CommandFactory factory = registry.get(verb);
        if (factory != null) {
            Command command = factory.create(args);
            if (command != null) {
                return Optional.of(command);
            }
        }
        return Optional.of(new UnknownCommand(stripped));
    }

    private void register(String verb, CommandFactory factory) {
        registry.put(verb, factory);
    }

    /** Joins the argument tokens back into one target phrase (whitespace collapsed to single spaces). */
    private static String joinRemainder(List<String> args) {
        return String.join(" ", args);
    }

    /** A short, all-digits token that parses to a strictly positive int — a 1-based menu pick. */
    private static boolean isMenuNumber(String token) {
        if (token.isEmpty() || token.length() > 9 || !token.chars().allMatch(Character::isDigit)) {
            return false;
        }
        return Integer.parseInt(token) >= 1;
    }
}
