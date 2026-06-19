package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.calendar.GameDate;
import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.port.calendar.CalendarSourceOperationsOutputPort;
import com.github.gameclean.core.port.clock.GameTimeSourceOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Optional;

/**
 * Reports the current game date. Implementation of {@link AskForTimeInputPort}; framework-free, wired by the
 * composition root, exercised in isolation against mocked ports.
 *
 * <p>A <b>read-only</b> interaction, like {@code look}: it reads, derives, and presents, with <em>no
 * transaction</em>. It orchestrates — the arithmetic lives on the model. It loads the authored calendar,
 * loads the persisted {@link GameClock}, asks the time source how long the live session has run, lets the
 * clock combine the two into the current elapsed seconds ({@link GameClock#elapsedWith(long)}), and lets the
 * calendar place that instant ({@link GameCalendar#placeInstant(long)}). The resulting {@link GameDate}, with
 * the calendar that frames it, goes to the presenter.
 *
 * <p>The clock is created at game initialization, so a missing clock means the game is not yet in a playable
 * state — an <em>anticipated precondition</em>, handled by <b>branch-and-present</b>
 * ({@code presentGameNotInitialized}) and a {@code return}, never by throwing a technical exception into the
 * catch-all. Only genuine faults (a {@code PersistenceOperationsError}, a {@code CalendarSourceOperationsError},
 * an unexpected bug) ride the outermost {@code catch} to {@code presentError}. Exactly one {@code present*} is
 * reached on every path.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AskForTimeUseCase implements AskForTimeInputPort {

    AskForTimePresenterOutputPort presenter;
    CalendarSourceOperationsOutputPort calendarSourceOps;
    GameClockRepositoryOperationsOutputPort gameClockRepositoryOps;
    GameTimeSourceOutputPort gameTimeSourceOps;

    @Override
    public void playerChecksTheTime() {
        try {
            // Precondition: the game must be in a playable state. A missing clock is an anticipated
            // outcome, presented and returned — not thrown into the catch-all below.
            Optional<GameClock> clock = gameClockRepositoryOps.findClock();
            if (clock.isEmpty()) {
                presenter.presentGameNotInitialized();
                return;
            }

            GameCalendar calendar = calendarSourceOps.loadCalendar();
            long elapsed = clock.get().elapsedWith(gameTimeSourceOps.elapsedSessionSeconds());
            GameDate now = calendar.placeInstant(elapsed);
            presenter.presentCurrentTime(now, calendar);

        } catch (Exception e) {
            // Outermost checkpoint: only genuine faults reach here — a PersistenceOperationsError, a
            // CalendarSourceOperationsError, or an unexpected bug.
            presenter.presentError(e);
        }
    }
}
