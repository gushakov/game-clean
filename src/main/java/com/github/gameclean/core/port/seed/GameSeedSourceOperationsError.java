package com.github.gameclean.core.port.seed;

/**
 * Unchecked failure of a {@link GameSeedSourceOperationsOutputPort} operation — the authored seed could
 * not be read or parsed (a missing resource, an I/O error, a malformed authoring fraction). Caught at the
 * use case's single outermost checkpoint, which translates it into {@code presentError}.
 *
 * <p>Unchecked on purpose, mirroring {@link com.github.gameclean.core.port.persistence.PersistenceOperationsError}:
 * a broken seed file is an infrastructure fault, and routing it through the use case's catch-all presents
 * it uniformly with every other infrastructure failure rather than failing startup from inside an adapter.
 */
public class GameSeedSourceOperationsError extends RuntimeException {

    public GameSeedSourceOperationsError(String message) {
        super(message);
    }

    public GameSeedSourceOperationsError(String message, Throwable cause) {
        super(message, cause);
    }
}
