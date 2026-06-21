package com.github.gameclean.core.port.concurrency;

/**
 * Unchecked boundary error signalling that a persisted aggregate was <b>modified concurrently</b>: a write
 * carried a version the store had already moved past, so it was rejected rather than overwriting the newer
 * state. The canonical DDD detector for a concurrent modification of one aggregate (design-notes §5).
 *
 * <p><b>Why it lives in its own {@code concurrency} package — raiser is not owner.</b> A concurrent-modification
 * conflict touches three different owners, and belongs conceptually to none of the obvious two. The
 * <em>aggregate</em> owns the concept (it carries the optimistic {@code version}); the <em>persistence</em>
 * adapter <em>detects</em> the conflict (the store enforces the version check, so a driven adapter raises this
 * by wrapping its framework's optimistic-locking failure — e.g. Spring's {@code OptimisticLockingFailureException}
 * — so a use case never catches a raw framework type); and the <em>transaction</em> boundary is what
 * <em>rolls back</em> when the detector fires. It is thus <em>both</em> a persistence and a transaction concern
 * by implementation, but <em>neither</em> by concept — a concurrency-control signal. Housing it in either port's
 * package would force the other boundary's adapter to type-couple to a sibling port's currency; a neutral
 * package both depend on symmetrically removes that coupling (design-notes §5).
 *
 * <p><b>Not a fault — an expected outcome under concurrency.</b> Deliberately a plain {@link RuntimeException}
 * (like the persistence and transaction port errors), <em>not</em> a subtype of either {@code
 * PersistenceOperationsError} or {@code TransactionOperationsError}: an optimistic-lock conflict is not "the
 * store is broken" or "demarcation failed", it is "someone else got there first" — a normal outcome the caller
 * typically reacts to differently (skip, retry, or treat as already-done) rather than reporting as a fault. It
 * is reacted to either by the use case (catch and present a domain outcome) or, as an opt-in idiom, by the
 * transaction port's {@code doInTransaction(action, onLockDetected)} handler. The day-phase announcement, for
 * instance, presents "nothing to announce": the loser's intent was already fulfilled by the winner.
 *
 * <p><b>No parent hierarchy yet.</b> The §5 detector lens predicts siblings — a unique-constraint race, a
 * serialization failure — that would one day justify a {@code ConcurrencyConflictError} parent here. That is
 * deferred by emergence: mint the parent when the second detector actually needs a home, not before.
 */
public class OptimisticLockingError extends RuntimeException {

    public OptimisticLockingError(String message) {
        super(message);
    }

    public OptimisticLockingError(String message, Throwable cause) {
        super(message, cause);
    }
}
