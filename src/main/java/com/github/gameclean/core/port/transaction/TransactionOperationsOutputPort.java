package com.github.gameclean.core.port.transaction;

import java.util.function.Supplier;

/**
 * Driven (output) port for explicit transaction demarcation. A use case calls it to run its
 * persistence writes inside a narrow transaction and presents only once the call has returned —
 * instead of wrapping the whole interaction in a blanket {@code @Transactional}.
 *
 * <p>The methodology shape, kept deliberately lean:
 * <ul>
 *   <li><b>Validation and reads happen outside</b> {@code doInTransaction}; only persistence (and,
 *       later, event dispatch) need the consistency boundary inside it.</li>
 *   <li><b>Presentation happens after commit</b>, so the actor is never told an operation succeeded
 *       while the transaction might still roll back. It is the plain statement after the block (branching,
 *       where the block settles more than one outcome, on what it returned): statement order is the
 *       after-commit order, and a rollback skips what follows by throwing. The block holds the write set
 *       and nothing else — the port offers no after-commit hook.</li>
 *   <li><b>Failure is expressed by throwing</b> — an unchecked error thrown inside the action rolls
 *       the transaction back and propagates to the use case's single outermost checkpoint. There is
 *       deliberately no {@code rollback()} method.</li>
 *   <li><b>The machinery has its own failure currency</b> — a fault in <em>demarcation</em> (begin,
 *       commit, or unexpected rollback), as opposed to a fault in the action, surfaces as a
 *       {@link TransactionOperationsError}, this port's own boundary type. The adapter translates the
 *       underlying framework exception so the use case never catches a raw transaction exception.</li>
 * </ul>
 *
 * <p>Actions are plain {@link Runnable} / {@link Supplier}: persistence ports raise <em>unchecked</em>
 * errors (see {@code PersistenceOperationsError}), so no checked-exception plumbing is needed and a
 * thrown error triggers Spring's default rollback-on-runtime.
 */
public interface TransactionOperationsOutputPort {

    /** Convenience overload for a read-write transaction. */
    default void doInTransaction(Runnable action) {
        doInTransaction(false, action);
    }

    /**
     * Runs {@code action} inside a transaction (joining the current one if any is active).
     *
     * @param readOnly {@code true} for a read-only transaction
     */
    void doInTransaction(boolean readOnly, Runnable action);

    /** Convenience overload for a read-write transaction returning a result. */
    default <T> T doInTransactionWithResult(Supplier<T> action) {
        return doInTransactionWithResult(false, action);
    }

    /**
     * Runs {@code action} inside a transaction and returns its result (joining the current
     * transaction if any is active).
     *
     * @param readOnly {@code true} for a read-only transaction
     */
    <T> T doInTransactionWithResult(boolean readOnly, Supplier<T> action);

    /**
     * Runs {@code action} inside a read-write transaction and returns what it settled: the action's own
     * result when the transaction commits, or {@code onLockDetected} when the action's versioned write loses
     * a concurrent race. In the second case the adapter has already rolled the transaction back before it
     * returns — a lost race is an expected concurrency outcome, not a fault, so it comes back as a value
     * instead of propagating as an {@code OptimisticLockingError} (the {@code core.port.concurrency} type).
     * Every other error still propagates to the use case's outermost checkpoint. Always read-write: a
     * read-only transaction issues no versioned write, so there is no lock to detect.
     *
     * <p><b>The block holds the write set and nothing else</b> — no presentation, no subcase call, no helper
     * opening a transaction of its own. The interaction presents <em>after</em> this call, as plain
     * statements branching on the returned value, so statement order is the after-commit order and exactly
     * one {@code present*} is reached on every path (design-notes §4: presentation is terminal).
     *
     * <p><b>The loss side is a value, not a handler.</b> By the time a loss is detected the transaction is
     * over, so a handler would run in exactly the state the statement after this call runs in — same thread,
     * no transaction, same locals; the only thing it knows that later code does not is <em>that</em> the race
     * was lost, which is what the returned value carries. Anything else the reaction needs (a fresh read
     * naming the winner, say) belongs in the caller's conflict branch after the block.
     *
     * <p><b>{@code T} is the interaction's own outcome type</b>, private to its use case: a two-constant enum
     * where the block yields only "committed or lost", a sealed type with value cases where it yields data.
     *
     * <p><b>The block must be the outermost transaction.</b> Joined to an active one, a swallowed loss would
     * hand back {@code onLockDetected} while the enclosing transaction is already marked rollback-only, the
     * failure surfacing later and elsewhere. The adapter therefore refuses to run this inside an active
     * transaction, with a {@link TransactionOperationsError}.
     *
     * @param action         the write set; returns the outcome of a committed transaction
     * @param onLockDetected the outcome to return when the versioned write loses the race; never {@code null}
     */
    <T> T doInTransactionWithResult(Supplier<T> action, T onLockDetected);
}
