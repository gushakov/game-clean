package com.github.gameclean.core.port.transaction;

/**
 * Unchecked failure of the transaction machinery itself — the {@link TransactionOperationsOutputPort}
 * could not begin, commit, or roll back a transaction (a connection could not be acquired, the commit
 * failed, the transaction rolled back unexpectedly). Caught at the use case's single outermost
 * checkpoint, which translates it into {@code presentError}.
 *
 * <p>This is the transaction port's failure currency, mirroring
 * {@link com.github.gameclean.core.port.persistence.PersistenceOperationsError} and
 * {@link com.github.gameclean.core.port.seed.GameSeedSourceOperationsError}: a use case only ever
 * catches a port-declared error, never a raw {@code org.springframework.transaction.TransactionException}.
 * It is deliberately distinct from {@code PersistenceOperationsError} — a failure to <em>demarcate</em> a
 * transaction is the transaction port's concern, not the persistence port's, so each port owns its own
 * boundary type.
 *
 * <p>Crucially, the adapter wraps <em>only</em> the transaction machinery's own faults into this type.
 * A {@code PersistenceOperationsError} thrown by the action running <em>inside</em> the transaction is
 * already a port type and passes through untouched — it must, both to avoid double-wrapping and because
 * it is what triggers Spring's rollback-on-runtime.
 */
public class TransactionOperationsError extends RuntimeException {

    public TransactionOperationsError(String message) {
        super(message);
    }

    public TransactionOperationsError(String message, Throwable cause) {
        super(message, cause);
    }
}
