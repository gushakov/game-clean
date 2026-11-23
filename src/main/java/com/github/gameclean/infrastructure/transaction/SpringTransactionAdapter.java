package com.github.gameclean.infrastructure.transaction;

import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.transaction.TransactionOperationsError;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Default implementation of {@link TransactionOperationsOutputPort} over Spring's transaction SPI.
 *
 * <p>Two {@link TransactionTemplate}s are injected — a read-write one and a read-only one — so the
 * {@code readOnly} flag selects the right propagation/isolation without per-call configuration.
 *
 * <p>Two failure modes, kept strictly apart. A runtime error thrown by the <em>action</em> (a
 * {@code PersistenceOperationsError}) propagates out of the template untouched, which triggers Spring's
 * default rollback-on-runtime; it is already a port type, so the use case's outermost checkpoint sees
 * it as-is. A failure of the transaction <em>machinery</em> itself — begin (no connection), commit, or
 * an unexpected rollback — surfaces as a Spring {@link TransactionException}, which this adapter catches
 * <em>narrowly</em> and wraps into the port's own {@link TransactionOperationsError}. The catch is
 * deliberately {@code TransactionException}, never {@code Exception}: a broad catch would re-wrap the
 * action's already-translated {@code PersistenceOperationsError} (double-wrapping) and blur the two
 * failure modes into one.
 *
 * <p>The lock-aware {@code (action, onLockDetected)} overload adds a third reaction, distinct from those two
 * demarcation faults. An {@link OptimisticLockingError} — raised by the <em>persistence</em> adapter when a
 * versioned write loses a concurrent race, and propagated through the template as a rollback-triggering
 * runtime exception — is caught here and turned into the caller's loss value instead of reaching the use
 * case's outermost checkpoint, because a lost race is an expected concurrency outcome, not a machinery
 * failure. Note the cross-boundary direction: the conflict is <em>detected</em> by persistence but
 * <em>reacted to</em> here, which is exactly why its type sits in the neutral {@code core.port.concurrency}
 * package both adapters depend on rather than in either port's own (design-notes §5). The catch sits around
 * the template, which has already rolled back and rethrown, so returning the loss value happens outside the
 * transaction — the very state the statement after the call runs in. Catching around the template is enough
 * because Spring Data JDBC issues the versioned {@code UPDATE} at {@code save}: the loss is thrown from inside
 * the action, never from the commit. The overload refuses to run inside an active transaction — a joined
 * block would swallow the loss while the enclosing transaction is already rollback-only.
 *
 * <p>There is deliberately no after-commit or after-rollback hook. Every interaction presents as a plain
 * statement after its block, so statement order is the after-commit order and a rollback skips what follows
 * by throwing; no synchronization callback is needed, and none can swallow a failure (design-notes §5).
 *
 * <p>No cache concern is wired here: the project has no caching layer, so there is nothing to
 * invalidate on rollback. Should one appear, the methodology's {@code CacheInvalidationOnRollback}
 * callback seam is introduced then — keeping this adapter decoupled from the cache.
 */
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SpringTransactionAdapter implements TransactionOperationsOutputPort {

    TransactionTemplate transactionTemplate;
    TransactionTemplate readOnlyTransactionTemplate;

    @Override
    public void doInTransaction(boolean readOnly, Runnable action) {
        log.debug("[Transaction] Running action in a transaction, readOnly={}", readOnly);
        try {
            template(readOnly).executeWithoutResult(status -> action.run());
        } catch (TransactionException e) {
            throw new TransactionOperationsError("Transaction could not be completed", e);
        }
    }

    @Override
    public <T> T doInTransactionWithResult(boolean readOnly, Supplier<T> action) {
        log.debug("[Transaction] Running action (with result) in a transaction, readOnly={}", readOnly);
        try {
            return template(readOnly).execute(status -> action.get());
        } catch (TransactionException e) {
            throw new TransactionOperationsError("Transaction could not be completed", e);
        }
    }

    @Override
    public <T> T doInTransactionWithResult(Supplier<T> action, T onLockDetected) {
        Objects.requireNonNull(onLockDetected, "onLockDetected");
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            // Joined to an enclosing transaction, a swallowed loss would return onLockDetected while that
            // transaction is already rollback-only. Refuse on every run, not only when the race is lost.
            throw new TransactionOperationsError(
                    "A lock-detecting transaction block must be the outermost transaction");
        }
        try {
            // Delegating keeps the narrow TransactionException wrapping in one place.
            return doInTransactionWithResult(false, action);
        } catch (OptimisticLockingError e) {
            // The action's versioned write lost a concurrent race. The template has rolled back and rethrown,
            // so the transaction is over here: the loss comes back as the caller's own outcome value.
            log.debug("[Transaction] Optimistic-lock conflict detected; returning the onLockDetected result", e);
            return onLockDetected;
        }
    }

    private TransactionTemplate template(boolean readOnly) {
        return readOnly ? readOnlyTransactionTemplate : transactionTemplate;
    }
}
