package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.model.calendar.GameCalendar;
import com.github.gameclean.core.model.calendar.GameDate;
import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.model.daytime.DayPhase;
import com.github.gameclean.core.model.daytime.DayPhaseLog;
import com.github.gameclean.core.port.calendar.CalendarSourceOperationsOutputPort;
import com.github.gameclean.core.port.clock.GameTimeSourceOutputPort;
import com.github.gameclean.core.port.daytime.DayPhaseScheduleSourceOperationsOutputPort;
import com.github.gameclean.core.port.persistence.DayPhaseLogRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.OptimisticLockingError;
import com.github.gameclean.core.port.randomness.RandomnessOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Optional;

/**
 * Announces the dawn/dusk lines that mark a new day phase as game time advances. Implementation of
 * {@link AnnounceTimeOfDayInputPort}; framework-free, wired by the composition root, exercised in isolation
 * against mocked ports. The initiating actor is the <em>system</em> (the background time ticker fires it on a
 * fixed real-time interval), so there is no security assertion.
 *
 * <p><b>A blind metronome drives a smart, idempotent interaction.</b> The ticker carries no time knowledge; it
 * just fires this repeatedly. So this use case owns all of it: derive "now" (like {@code AskForTime} — load
 * the clock, ask the time source how long the session ran, let the clock {@link GameClock#elapsedWith(long)}
 * combine them, let the calendar {@link GameCalendar#placeInstant(long)} place the instant), find the day
 * phase (if any) beginning at the current hour, and dedup against a persisted watermark so a phase is
 * announced once per occurrence no matter how many polls fall inside its hour.
 *
 * <p><b>The patterns this composes are not new.</b> A missing clock is an anticipated precondition, handled by
 * <b>branch-and-present</b> ({@code presentGameNotInitialized}) and a {@code return} — never thrown into the
 * catch-all (design-notes §4). The random message pick runs <em>outside</em> the transaction (a pure choice
 * with no persistence effect, exactly like the item-spawn rolls), with the entropy injected through
 * {@link RandomnessOperationsOutputPort} so the interaction stays deterministic under test.
 *
 * <p><b>Concurrency is arbitrated by the aggregate's version, not the transaction boundary.</b> The boundary
 * gives atomic rollback, not isolation — two observers could both read the log as pending and both write
 * (design-notes §5: atomicity ≠ isolation). So the guard is optimistic locking carried <em>on the
 * aggregate</em>: the log is read once (capturing its {@code version}), {@link DayPhaseLog#announceThrough}
 * carries that version onto the advanced log, and the single save inside {@code doInTransaction} is checked
 * against it. A concurrent observer that advanced the watermark first makes this write stale — the adapter
 * raises {@link OptimisticLockingError}, the transaction rolls back, and that is caught here and presented as
 * {@code presentNothingToAnnounce}: the loser's goal was already met, so there is nothing left to say (never a
 * double announcement). No re-read inside the transaction is needed; the version the use case read is the
 * whole guard. The success is deferred to after-commit, so it is never reported before the watermark is durable.
 *
 * <p><b>Every path presents exactly once.</b> The quiet observation — no phase at this hour, or one already
 * past the watermark — is a real outcome ({@code presentNothingToAnnounce}), presented immediately with no
 * transaction (a pure read, like {@code look}); it is not a silent return; the concurrent-loss outcome above
 * presents the same way. The lone success ({@code presentDayPhaseBegan}) is deferred to after-commit. Only
 * genuine faults (a {@code PersistenceOperationsError}, a {@code CalendarSourceOperationsError}, a
 * transaction-demarcation failure, an unexpected bug) ride the outermost {@code catch} to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AnnounceTimeOfDayUseCase implements AnnounceTimeOfDayInputPort {

    AnnounceTimeOfDayPresenterOutputPort presenter;
    CalendarSourceOperationsOutputPort calendarSourceOps;
    DayPhaseScheduleSourceOperationsOutputPort dayPhaseScheduleSourceOps;
    GameClockRepositoryOperationsOutputPort gameClockRepositoryOps;
    DayPhaseLogRepositoryOperationsOutputPort dayPhaseLogRepositoryOps;
    GameTimeSourceOutputPort gameTimeSourceOps;
    RandomnessOperationsOutputPort randomnessOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void systemObservesTimeOfDay() {
        try {
            // Precondition: the game must be in a playable state. A missing clock is an anticipated outcome
            // (the ticker may fire before the world is seeded), presented and returned — not thrown.
            Optional<GameClock> clock = gameClockRepositoryOps.findClock();
            if (clock.isEmpty()) {
                presenter.presentGameNotInitialized();
                return;
            }

            // Derive "now" and the absolute game hour it falls in — both are calendar arithmetic the calendar
            // owns (it owns the radices); the use case only orchestrates, outside any transaction.
            GameCalendar calendar = calendarSourceOps.loadCalendar();
            long elapsed = clock.get().elapsedWith(gameTimeSourceOps.elapsedSessionSeconds());
            GameDate now = calendar.placeInstant(elapsed);
            long absoluteHour = calendar.absoluteHourOf(elapsed);

            // Is a phase due, and not yet announced? Read the log (capturing its optimistic-locking version);
            // it is seeded at initialization beside the clock, so treat an unexpectedly absent log as "nothing
            // announced yet" rather than throwing.
            Optional<DayPhase> beginning = dayPhaseScheduleSourceOps.loadDayPhases().phaseBeginningAt(now.getHourIndex());
            DayPhaseLog log = dayPhaseLogRepositoryOps.findDayPhaseLog().orElseGet(DayPhaseLog::initial);
            if (beginning.isEmpty() || !log.isPending(absoluteHour)) {
                presenter.presentNothingToAnnounce();
                return;
            }

            // A new phase has begun. Pick its line outside the transaction (a pure choice, no persistence
            // effect) — the same place the item-spawn rolls run. The advanced log carries the version we read,
            // so the write below is checked against it; no re-read inside the transaction is needed.
            DayPhase phase = beginning.get();
            String message = phase.pickMessage(randomnessOps::nextDouble);
            DayPhaseLog advanced = log.announceThrough(absoluteHour);

            // One write, one atomic unit: save the advance (optimistically version-checked) and announce only
            // after it commits. A concurrent observer that advanced the watermark since our read makes this
            // write stale — surfaced as OptimisticLockingError below, never a double announcement.
            txOps.doInTransaction(false, () -> {
                dayPhaseLogRepositoryOps.saveDayPhaseLog(advanced);
                txOps.doAfterCommit(() -> presenter.presentDayPhaseBegan(phase, message));
            });

        } catch (OptimisticLockingError e) {
            // A concurrent observer announced this phase first: its version won, our write was rejected (and
            // rolled back). The loser's goal is already met, so there is simply nothing left to announce — an
            // expected outcome under concurrency, not a fault.
            presenter.presentNothingToAnnounce();
        } catch (Exception e) {
            // Outermost checkpoint: only genuine faults reach here — a PersistenceOperationsError (already
            // rolled back), a CalendarSourceOperationsError, a TransactionOperationsError, or an unexpected bug.
            presenter.presentError(e);
        }
    }
}
