package com.github.gameclean.infrastructure.terminal.command;

import lombok.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.Function;

/**
 * Turns a raw input line into a {@link Command} intent — a tokenizer plus a small command
 * <em>registry</em>. Each verb is registered with an expected argument arity and a factory that
 * builds its command; parsing is verb lookup + arity check + factory call. Adding a command (or a
 * synonym, as {@code bye}/{@code quit} show) is a single {@link #register} line, which is the whole
 * point: the language is programmable from one place, with no parser-generator machinery for what is
 * still a {@code verb [word]} grammar. The seam is intentionally shaped so a richer grammar (ANTLR or
 * otherwise) could replace this class without touching the console controller or the core.
 *
 * <p>Anything that does not match a registered verb at the right arity becomes an
 * {@link UnknownCommand} carrying the original input; blank input yields no command at all.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
public class CommandParser {

    private final Map<String, CommandDefinition> registry = new HashMap<>();

    public CommandParser() {
        register("look", 0, args -> new LookCommand());
        register("move", 1, args -> new MoveCommand(args.get(0)));
        register("go", 1, args -> new MoveCommand(args.get(0)));
        register("now", 0, args -> new TimeCommand());
        register("time", 0, args -> new TimeCommand());
        register("bye", 0, args -> new QuitCommand());
        register("quit", 0, args -> new QuitCommand());
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
        String verb = tokens[0].toLowerCase(Locale.ROOT);
        List<String> args = Arrays.asList(tokens).subList(1, tokens.length);

        CommandDefinition definition = registry.get(verb);
        if (definition == null || args.size() != definition.getArity()) {
            return Optional.of(new UnknownCommand(stripped));
        }
        return Optional.of(definition.getFactory().apply(args));
    }

    private void register(String verb, int arity, Function<List<String>, Command> factory) {
        registry.put(verb, new CommandDefinition(arity, factory));
    }

    /** Registry entry: how many arguments a verb takes and how to build its command. */
    @Value
    private static class CommandDefinition {
        int arity;
        Function<List<String>, Command> factory;
    }
}
