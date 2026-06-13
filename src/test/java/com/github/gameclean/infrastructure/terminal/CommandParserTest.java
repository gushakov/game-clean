package com.github.gameclean.infrastructure.terminal;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the tokenizer + command registry. No Spring, no terminal — just string in, intent
 * out. Covers the registered verbs, synonyms, case- and whitespace-insensitivity, blank input, and
 * the unknown / wrong-arity fall-throughs.
 */
class CommandParserTest {

    private final CommandParser parser = new CommandParser();

    @Test
    void parses_look_into_a_look_command() {
        assertThat(parser.parse("look")).contains(new LookCommand());
    }

    @Test
    void is_case_and_whitespace_insensitive() {
        assertThat(parser.parse("  LOOK  ")).contains(new LookCommand());
    }

    @Test
    void parses_bye_and_quit_into_a_quit_command() {
        assertThat(parser.parse("bye")).contains(new QuitCommand());
        assertThat(parser.parse("quit")).contains(new QuitCommand());
    }

    @Test
    void blank_input_yields_no_command() {
        assertThat(parser.parse("")).isEmpty();
        assertThat(parser.parse("   ")).isEmpty();
        assertThat(parser.parse(null)).isEmpty();
    }

    @Test
    void an_unknown_verb_becomes_an_unknown_command_carrying_the_input() {
        assertThat(parser.parse("dance")).contains(new UnknownCommand("dance"));
    }

    @Test
    void a_known_verb_with_unexpected_arguments_is_unknown_this_round() {
        // 'look <target>' is deferred — look takes no argument yet, so an argument makes it unknown.
        Optional<Command> parsed = parser.parse("look east");
        assertThat(parsed).contains(new UnknownCommand("look east"));
    }
}
