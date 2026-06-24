package com.github.gameclean.infrastructure.terminal.command;

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
    void parses_bare_look_into_a_look_command() {
        assertThat(parser.parse("look")).contains(new LookCommand());
    }

    @Test
    void is_case_and_whitespace_insensitive() {
        assertThat(parser.parse("  LOOK  ")).contains(new LookCommand());
    }

    @Test
    void parses_look_with_a_target_into_an_examine_command() {
        assertThat(parser.parse("look dagger")).contains(new ExamineCommand("dagger"));
    }

    @Test
    void parses_examine_and_its_x_synonym_carrying_the_target() {
        assertThat(parser.parse("examine dagger")).contains(new ExamineCommand("dagger"));
        assertThat(parser.parse("x dagger")).contains(new ExamineCommand("dagger"));
    }

    @Test
    void takes_the_whole_remainder_as_a_multi_word_target() {
        assertThat(parser.parse("look rusty sword")).contains(new ExamineCommand("rusty sword"));
        assertThat(parser.parse("examine  rusty   sword")).contains(new ExamineCommand("rusty sword"));
    }

    @Test
    void bare_examine_without_a_target_is_unknown() {
        assertThat(parser.parse("examine")).contains(new UnknownCommand("examine"));
    }

    @Test
    void parses_a_bare_positive_integer_into_a_select_command() {
        assertThat(parser.parse("2")).contains(new SelectCommand(2));
        assertThat(parser.parse("  10 ")).contains(new SelectCommand(10));
    }

    @Test
    void zero_and_non_numeric_are_not_selections() {
        // 0 is not a 1-based menu pick; it is not a verb either, so it falls through to unknown.
        assertThat(parser.parse("0")).contains(new UnknownCommand("0"));
        assertThat(parser.parse("-1")).contains(new UnknownCommand("-1"));
        assertThat(parser.parse("2 3")).contains(new UnknownCommand("2 3"));
    }

    @Test
    void parses_move_and_its_go_synonym_carrying_the_exit_name() {
        assertThat(parser.parse("move east")).contains(new MoveCommand("east"));
        assertThat(parser.parse("go east")).contains(new MoveCommand("east"));
    }

    @Test
    void move_without_an_exit_is_unknown_this_round() {
        // 'move' takes exactly one argument; bare 'move' fails the arity check.
        assertThat(parser.parse("move")).contains(new UnknownCommand("move"));
    }

    @Test
    void parses_now_and_its_time_synonym_into_a_time_command() {
        assertThat(parser.parse("now")).contains(new TimeCommand());
        assertThat(parser.parse("time")).contains(new TimeCommand());
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
    void a_known_verb_with_unexpected_arity_is_unknown_this_round() {
        // 'move' takes exactly one exit token; two arguments fit no factory form.
        Optional<Command> parsed = parser.parse("move east west");
        assertThat(parsed).contains(new UnknownCommand("move east west"));
    }
}
