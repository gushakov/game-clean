package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.daytime.DayPhaseLog;

import java.util.Optional;

/**
 * Driven (output) port for day-phase-log persistence — the {@code OutputPort} suffix marks the hexagonal
 * direction: the core is the caller, an infrastructure adapter the implementor.
 *
 * <p>Scoped to what the time-of-day announcement needs: a lookup (game initialization seeds the log only if
 * none exists; the announcement reads it to learn the watermark <em>and the version</em>) and a save
 * (initialization creates it at the "nothing announced" sentinel; the announcement advances it when a day
 * phase begins). Following the established driven-port convention it trades in the domain {@link DayPhaseLog}
 * model — the adapter hides its own persistence shape and the world-singleton row it lives in — and the
 * domain model carries the optimistic-locking version it read, so the save is checked against it.
 *
 * <p>Failures surface as unchecked port errors: a genuine persistence fault as {@link PersistenceOperationsError}
 * (shared with the other repositories), and — distinctly — a concurrent modification as
 * {@link OptimisticLockingError} when {@code saveDayPhaseLog} carries a version the store has moved past. The
 * caller reacts to the latter as an outcome ("someone announced first"), not a fault. Methods declare no
 * {@code throws} so {@code saveDayPhaseLog} composes directly as a transaction action.
 */
public interface DayPhaseLogRepositoryOperationsOutputPort {

    /**
     * @return the persisted day-phase log, or empty if the game has not been initialized yet.
     * @throws PersistenceOperationsError if the lookup fails (unreadable or corrupt)
     */
    Optional<DayPhaseLog> findDayPhaseLog();

    /**
     * Persists the day-phase log, inserting it if new and updating it in place otherwise (an upsert): game
     * initialization creates it at the sentinel; the announcement advances its watermark. There is exactly one
     * log, so the adapter hides the singleton row and the insert-vs-update decision.
     *
     * @throws PersistenceOperationsError if the save fails
     */
    void saveDayPhaseLog(DayPhaseLog log);
}
