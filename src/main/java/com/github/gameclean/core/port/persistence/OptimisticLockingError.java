package com.github.gameclean.core.port.persistence;

/**
 * Unchecked boundary error signalling that a persisted aggregate was <b>modified concurrently</b>: a write
 * carried a version the store had already moved past, so it was rejected rather than overwriting the newer
 * state. A driven adapter raises this by wrapping its framework's optimistic-locking failure (e.g. Spring's
 * {@code OptimisticLockingFailureException}), so a use case never catches a raw framework type.
 *
 * <p>Deliberately a <em>sibling</em> of {@link PersistenceOperationsError}, not a subtype: an optimistic-lock
 * conflict is not "the store is broken", it is "someone else got there first" — a normal, expected outcome
 * under concurrency that the caller typically reacts to differently (skip, retry, or treat as already-done)
 * rather than reporting as a fault. The day-phase announcement, for instance, catches this and presents
 * "nothing to announce": the loser's intent was already fulfilled by the winner. The canonical DDD detector
 * for a concurrent modification of one aggregate, surfaced as the persistence port's own currency
 * (design-notes §5).
 */
public class OptimisticLockingError extends RuntimeException {

    public OptimisticLockingError(String message) {
        super(message);
    }

    public OptimisticLockingError(String message, Throwable cause) {
        super(message, cause);
    }
}
