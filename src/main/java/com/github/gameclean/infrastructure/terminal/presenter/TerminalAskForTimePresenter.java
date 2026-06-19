package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.calendar.GameDate;
import com.github.gameclean.core.usecase.clock.AskForTimePresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.render.CalendarRenderer;
import com.github.gameclean.infrastructure.terminal.render.Console;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

/**
 * Secondary (driven) adapter rendering the {@code AskForTime} use case's outcome to the shared JLine console.
 * It delegates the date rendering to the shared {@link CalendarRenderer} and carries only the
 * {@code presentError} catch-all with a use-case-specific log tag.
 *
 * <p>Like the other terminal presenters it is {@code new}ed by the composition root (not a {@code @Component}),
 * sharing the {@code CalendarRenderer}/{@code Console} <em>resources</em>.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalAskForTimePresenter implements AskForTimePresenterOutputPort {

    CalendarRenderer calendarRenderer;
    Console console;

    @Override
    public void presentCurrentTime(GameDate date, GameCalendar calendar) {
        calendarRenderer.renderCurrentTime(date, calendar);
    }

    @Override
    public void presentGameNotInitialized() {
        console.printError("The game has not been initialized yet.");
    }

    @Override
    public void presentError(Exception e) {
        log.error("[AskForTime] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
