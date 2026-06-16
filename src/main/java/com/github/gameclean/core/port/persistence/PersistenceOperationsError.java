package com.github.gameclean.core.port.persistence;

/**
 * Unchecked failure of a persistence operation, raised by {@link SceneRepositoryOperationsOutputPort}
 * and caught at the use-case's single outermost checkpoint, which translates it into a presenter call.
 *
 * <p>Unchecked on purpose: it lets persistence actions run as plain {@code Runnable}/{@code Supplier}
 * inside {@link com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort}, and a
 * thrown error triggers Spring's default rollback-on-runtime — no checked-exception plumbing across
 * the transaction boundary. The use case still handles it explicitly at its {@code catch} checkpoint.
 */
public class PersistenceOperationsError extends RuntimeException {

    public PersistenceOperationsError(String message) {
        super(message);
    }

    public PersistenceOperationsError(String message, Throwable cause) {
        super(message, cause);
    }
}
