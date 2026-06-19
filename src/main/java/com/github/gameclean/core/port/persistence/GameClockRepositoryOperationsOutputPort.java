package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.clock.GameClock;

import java.util.Optional;

/**
 * Driven (output) port for game-clock persistence — the {@code OutputPort} suffix marks the hexagonal
 * direction: the core is the caller, an infrastructure adapter the implementor.
 *
 * <p>Scoped to what its use cases need: a lookup (game initialization seeds the clock only if none exists;
 * the time-reading and suspend use cases load it) and a save (initialization creates it at zero; suspend
 * banks the session's elapsed seconds into it). Following the established driven-port convention it trades in
 * the domain {@link GameClock} model — the adapter hides its own persistence shape and the world-singleton
 * row it lives in.
 *
 * <p>Failures surface as the unchecked {@link PersistenceOperationsError}; methods declare no {@code throws}
 * so {@code saveClock} composes directly as a transaction action.
 */
public interface GameClockRepositoryOperationsOutputPort {

    /**
     * @return the persisted world clock, or empty if the game has not been initialized yet.
     * @throws PersistenceOperationsError if the lookup fails (unreadable or corrupt)
     */
    Optional<GameClock> findClock();

    /**
     * Persists the world clock, inserting it if new and updating it in place otherwise (an upsert): game
     * initialization creates it; suspend updates the banked total. There is exactly one clock, so the adapter
     * hides the singleton row and the insert-vs-update decision.
     *
     * @throws PersistenceOperationsError if the save fails
     */
    void saveClock(GameClock clock);
}
