package com.github.gameclean.infrastructure.terminal;

import lombok.Value;

/**
 * Intent: the player wants to know the current date and time. Carries no argument, so it is a pure marker
 * the console maps to {@code AskForTimeInputPort.playerChecksTheTime()}.
 */
@Value
public class TimeCommand implements Command {
}
