package com.github.gameclean.infrastructure.terminal;

import lombok.Value;

/**
 * Intent: the input matched no registered command (unknown verb, or a known verb with the wrong
 * number of arguments). Carries the original input so the console can echo it back in a helpful
 * message.
 */
@Value
public class UnknownCommand implements Command {

    String input;
}
