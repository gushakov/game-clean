package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.model.daytime.DayPhase;
import com.github.gameclean.core.model.daytime.DayPhaseLog;
import com.github.gameclean.core.model.daytime.DayPhaseSchedule;
import com.github.gameclean.core.port.calendar.CalendarSourceOperationsOutputPort;
import com.github.gameclean.core.port.clock.GameTimeSourceOutputPort;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.daytime.DayPhaseScheduleSourceOperationsOutputPort;
import com.github.gameclean.core.port.persistence.DayPhaseLogRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.randomness.RandomnessOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static com.github.gameclean.core.model.calendar.CalendarFixtures.standard;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runLockAwareTransactionAndFireAfterCommit;
import static com.github.gameclean.core.usecase.TransactionPortStubs.runLockAwareTransactionDetectingLock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Interaction tests for {@link AnnounceTimeOfDayUseCase} in isolation — every output port is mocked. The use
 * case orchestrates (derive "now", find the phase due, dedup against the watermark) and lets the model decide
 * (the calendar places the instant, the schedule resolves the phase, the phase picks the message, the log
 * judges pending). The standard calendar has 300-second hours, so a dawn at hour 6 falls at absolute hour 6
 * (elapsed 1800s / 300).
 *
 * <p>The transaction port is stubbed to run its action inline and fire after-commit callbacks immediately,
 * exactly as {@code InitializeGameUseCaseTest} does — so a deferred presentation is observed synchronously.
 * Randomness is pinned (0.0 → the first message) to make the pick reproducible.
 */
@ExtendWith(MockitoExtension.class)
class AnnounceTimeOfDayUseCaseTest {

    @Mock
    private AnnounceTimeOfDayPresenterOutputPort presenter;
    @Mock
    private CalendarSourceOperationsOutputPort calendarSourceOps;
    @Mock
    private DayPhaseScheduleSourceOperationsOutputPort dayPhaseScheduleSourceOps;
    @Mock
    private GameClockRepositoryOperationsOutputPort gameClockRepositoryOps;
    @Mock
    private DayPhaseLogRepositoryOperationsOutputPort dayPhaseLogRepositoryOps;
    @Mock
    private GameTimeSourceOutputPort gameTimeSourceOps;
    @Mock
    private RandomnessOperationsOutputPort randomnessOps;
    @Mock
    private TransactionOperationsOutputPort txOps;

    @InjectMocks
    private AnnounceTimeOfDayUseCase useCase;

    @Test
    void announcesAPhaseThatHasBegunAndAdvancesTheWatermarkAfterCommit() {
        givenTimeAt(1800);                       // hour 6 of day 0, absolute hour 6
        when(dayPhaseScheduleSourceOps.loadDayPhases()).thenReturn(dawnAtHourSix());
        when(dayPhaseLogRepositoryOps.findDayPhaseLog()).thenReturn(Optional.of(DayPhaseLog.initial()));
        when(randomnessOps.nextDouble()).thenReturn(0.0);   // pick the first message
        runLockAwareTransactionAndFireAfterCommit(txOps);

        useCase.systemObservesTimeOfDay();

        verify(dayPhaseLogRepositoryOps).saveDayPhaseLog(new DayPhaseLog(6, 0));
        verify(presenter).presentDayPhaseBegan(dawn(), "Dawn breaks.");
        verify(presenter, never()).presentNothingToAnnounce();
    }

    @Test
    void announcesNothingWhenNoPhaseBeginsAtThisHour() {
        givenTimeAt(2100);                       // hour 7 — no phase
        when(dayPhaseScheduleSourceOps.loadDayPhases()).thenReturn(dawnAtHourSix());

        useCase.systemObservesTimeOfDay();

        verify(presenter).presentNothingToAnnounce();
        verify(presenter, never()).presentDayPhaseBegan(any(), any());
        verify(dayPhaseLogRepositoryOps, never()).saveDayPhaseLog(any());
        verify(txOps, never()).doInTransaction(any(Runnable.class), any(Runnable.class));
    }

    @Test
    void announcesNothingWhenThePhaseAtThisHourWasAlreadyAnnounced() {
        givenTimeAt(1800);                       // hour 6, absolute hour 6
        when(dayPhaseScheduleSourceOps.loadDayPhases()).thenReturn(dawnAtHourSix());
        // Watermark already at hour 6 — dawn has been announced this occurrence.
        when(dayPhaseLogRepositoryOps.findDayPhaseLog()).thenReturn(Optional.of(new DayPhaseLog(6, 2)));

        useCase.systemObservesTimeOfDay();

        verify(presenter).presentNothingToAnnounce();
        verify(presenter, never()).presentDayPhaseBegan(any(), any());
        verify(dayPhaseLogRepositoryOps, never()).saveDayPhaseLog(any());
        verify(txOps, never()).doInTransaction(any(Runnable.class), any(Runnable.class));
    }

    @Test
    void announcesNothingWhenAConcurrentObserverWonTheOptimisticLock() {
        givenTimeAt(1800);                       // hour 6, absolute hour 6 — pending on our read
        when(dayPhaseScheduleSourceOps.loadDayPhases()).thenReturn(dawnAtHourSix());
        when(dayPhaseLogRepositoryOps.findDayPhaseLog()).thenReturn(Optional.of(DayPhaseLog.initial()));
        when(randomnessOps.nextDouble()).thenReturn(0.0);
        // The version-checked save loses to a concurrent advance that happened since our read.
        doThrow(new OptimisticLockingError("stale version"))
                .when(dayPhaseLogRepositoryOps).saveDayPhaseLog(any());
        runLockAwareTransactionDetectingLock(txOps);

        useCase.systemObservesTimeOfDay();

        verify(presenter).presentNothingToAnnounce();
        verify(presenter, never()).presentDayPhaseBegan(any(), any());
        verify(presenter, never()).presentError(any());
    }

    @Test
    void presentsGameNotInitializedWhenTheClockIsAbsent() {
        // The clock is checked first, before the calendar or schedule are even loaded.
        when(gameClockRepositoryOps.findClock()).thenReturn(Optional.empty());

        useCase.systemObservesTimeOfDay();

        verify(presenter).presentGameNotInitialized();
        verify(presenter, never()).presentDayPhaseBegan(any(), any());
        verify(presenter, never()).presentNothingToAnnounce();
        verify(presenter, never()).presentError(any());
    }

    @Test
    void routesAPersistenceFailureToTheCatchAll() {
        PersistenceOperationsError boom = new PersistenceOperationsError("database unavailable");
        when(gameClockRepositoryOps.findClock()).thenThrow(boom);

        useCase.systemObservesTimeOfDay();

        verify(presenter).presentError(boom);
        verify(presenter, never()).presentDayPhaseBegan(any(), any());
        verify(presenter, never()).presentNothingToAnnounce();
    }

    // --- fixtures -------------------------------------------------------------------------------

    /** Stub the clock, time source and calendar so derived elapsed time equals {@code elapsedSeconds}. */
    private void givenTimeAt(long elapsedSeconds) {
        when(gameClockRepositoryOps.findClock()).thenReturn(Optional.of(new GameClock(elapsedSeconds)));
        when(gameTimeSourceOps.elapsedSessionSeconds()).thenReturn(0L);
        when(calendarSourceOps.loadCalendar()).thenReturn(standard());
    }

    private static DayPhase dawn() {
        return new DayPhase("Dawn", 6, List.of("Dawn breaks.", "The light returns."));
    }

    private static DayPhaseSchedule dawnAtHourSix() {
        return new DayPhaseSchedule(List.of(dawn()));
    }
}
