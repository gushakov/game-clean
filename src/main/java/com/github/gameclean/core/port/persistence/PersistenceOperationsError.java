package com.github.gameclean.core.port.persistence;

/**
 * Checked failure of a persistence operation, raised by {@link PersistenceGatewayOutputPort}
 * and caught at the use-case checkpoint, which translates it into a presenter call.
 *
 * <p>Checked on purpose: a persistence failure is an expected, handleable outcome of an
 * interaction (not a programming error), so the use case must acknowledge it explicitly.
 */
public class PersistenceOperationsError extends Exception {

    public PersistenceOperationsError(String message) {
        super(message);
    }

    public PersistenceOperationsError(String message, Throwable cause) {
        super(message, cause);
    }
}
