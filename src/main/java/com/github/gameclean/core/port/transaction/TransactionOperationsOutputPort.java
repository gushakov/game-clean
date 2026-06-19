package com.github.gameclean.core.port.transaction;

import java.util.function.Supplier;

/**
 * Driven (output) port for explicit transaction demarcation. A use case calls it to run its
 * persistence writes inside a narrow transaction and to defer presentation until the transaction
 * actually commits — instead of wrapping the whole interaction in a blanket {@code @Transactional}.
 *
 * <p>The methodology shape, kept deliberately lean:
 * <ul>
 *   <li><b>Validation and reads happen outside</b> {@code doInTransaction}; only persistence (and,
 *       later, event dispatch) need the consistency boundary inside it.</li>
 *   <li><b>Presentation happens after commit</b> via {@link #doAfterCommit(Runnable)}, so the actor
 *       is never told an operation succeeded while the transaction might still roll back.</li>
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
     * Registers {@code action} to run after the current transaction commits. If no transaction is
     * active, runs it immediately (there is nothing to wait for).
     */
    void doAfterCommit(Runnable action);

    /**
     * Registers {@code action} to run after the current transaction rolls back. If no transaction is
     * active, this is a no-op — there was no rollback to react to (a validation error before
     * {@code doInTransaction} should present the failure directly).
     */
    void doAfterRollback(Runnable action);
}
