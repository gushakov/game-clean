package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.calendar.GameDate;
import com.github.gameclean.core.model.calendar.Month;
import com.github.gameclean.core.model.calendar.Weekday;
import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.port.calendar.CalendarSourceOperationsOutputPort;
import com.github.gameclean.core.port.clock.GameTimeSourceOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link AskForTimeUseCase} in isolation — every output port is mocked. The use case
 * orchestrates (load calendar, load clock, ask the time source how long the session ran) and lets the model
 * do the arithmetic ({@link GameClock#elapsedWith}, {@link GameCalendar#placeInstant}); the tests pin a known
 * banked total and session-elapsed and assert the resulting {@link GameDate}. A read-only interaction — there
 * is no transaction port to stub.
 */
@ExtendWith(MockitoExtension.class)
class AskForTimeUseCaseTest {

    @Mock
    private AskForTimePresenterOutputPort presenter;
    @Mock
    private CalendarSourceOperationsOutputPort calendarSourceOps;
    @Mock
    private GameClockRepositoryOperationsOutputPort gameClockRepositoryOps;
    @Mock
    private GameTimeSourceOutputPort gameTimeSourceOps;

    @InjectMocks
    private AskForTimeUseCase useCase;

    @Test
    void presentsTheDatePlacedFromTheBankedTotalPlusTheSession() {
        GameCalendar calendar = standardCalendar();
        when(calendarSourceOps.loadCalendar()).thenReturn(calendar);
        // 3_000s banked + 600s this session = 3_600s = 12 game hours into the first day of year 1000.
        when(gameClockRepositoryOps.findClock()).thenReturn(Optional.of(new GameClock(3_000)));
        when(gameTimeSourceOps.elapsedSessionSeconds()).thenReturn(600L);

        useCase.playerChecksTheTime();

        verify(presenter).presentCurrentTime(new GameDate(1000, 0, 0, 12, 0), calendar);
    }

    @Test
    void countsOnlyTheSessionWhenTheBankedTotalIsZero() {
        GameCalendar calendar = standardCalendar();
        when(calendarSourceOps.loadCalendar()).thenReturn(calendar);
        when(gameClockRepositoryOps.findClock()).thenReturn(Optional.of(GameClock.initial()));
        when(gameTimeSourceOps.elapsedSessionSeconds()).thenReturn(3_600L);

        useCase.playerChecksTheTime();

        verify(presenter).presentCurrentTime(new GameDate(1000, 0, 0, 12, 0), calendar);
    }

    @Test
    void presentsGameNotInitializedWhenTheClockIsAbsent() {
        // The clock is checked first, before the calendar is even loaded: a missing clock is an anticipated
        // outcome, branch-and-presented and returned — never thrown into the catch-all.
        when(gameClockRepositoryOps.findClock()).thenReturn(Optional.empty());

        useCase.playerChecksTheTime();

        verify(presenter).presentGameNotInitialized();
        verify(presenter, never()).presentCurrentTime(any(), any());
        verify(presenter, never()).presentError(any());
    }

    @Test
    void routesAPersistenceFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(gameClockRepositoryOps.findClock()).thenThrow(boom);

        useCase.playerChecksTheTime();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentGameNotInitialized();
        verify(presenter, never()).presentCurrentTime(any(), any());
    }

    /** The standard calendar pinned in GameCalendarTest: 300s hours, 24h days, 30-day months, 5 weekdays, 10 months. */
    private static GameCalendar standardCalendar() {
        return new GameCalendar(300, 24, 30,
                List.of(new Weekday("Elenya", "guidance"), new Weekday("Anarya", "vitality"),
                        new Weekday("Isilya", "reflection"), new Weekday("Alduya", "growth"),
                        new Weekday("Menelya", "higher matters")),
                List.of(new Month("Aelorin", "a"), new Month("Sylvael", "b"), new Month("Calivorn", "c"),
                        new Month("Thalinde", "d"), new Month("Evaniel", "e"), new Month("Miraleth", "f"),
                        new Month("Faerundel", "g"), new Month("Veloris", "h"), new Month("Aelindra", "i"),
                        new Month("Sorivael", "j")));
    }
}
