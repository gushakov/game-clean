package com.github.gameclean.infrastructure.transaction;

import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.transaction.TransactionOperationsError;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
 * <p>The {@code (action, onLockDetected)} overload adds a third, opt-in reaction, distinct from those two
 * demarcation faults. An {@link OptimisticLockingError} — raised by the <em>persistence</em> adapter when a
 * versioned write loses a concurrent race, and propagated through the template as a rollback-triggering
 * runtime exception — is caught here and routed to the supplied handler instead of to the use case's
 * outermost checkpoint, because a lost race is an expected concurrency outcome, not a machinery failure. Note
 * the cross-boundary direction: the conflict is <em>detected</em> by persistence but <em>reacted to</em> here,
 * which is exactly why its type sits in the neutral {@code core.port.concurrency} package both adapters depend
 * on rather than in either port's own (design-notes §5). With a {@code null} handler the overload is a plain
 * read-write transaction and the error propagates as usual.
 *
 * <p>After-commit / after-rollback hooks register a {@link TransactionSynchronization} and fire on
 * the matching completion status. With no transaction active, {@code doAfterCommit} runs immediately
 * (nothing to wait for) and {@code doAfterRollback} is a no-op (nothing rolled back).
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
    public void doInTransaction(Runnable action, Runnable onLockDetected) {
        if (onLockDetected == null) {
            // No handler supplied: behave exactly like a plain read-write transaction — a lost race
            // propagates as OptimisticLockingError to the use case's outermost checkpoint.
            doInTransaction(action);
            return;
        }
        try {
            doInTransaction(action);
        } catch (OptimisticLockingError e) {
            // The action's versioned write lost a concurrent race: the detector fired and the transaction
            // already rolled back. That is an expected outcome under concurrency, not a fault, so the
            // caller's handler reacts to it (e.g. present "nothing to do") instead of it reaching presentError.
            log.debug("[Transaction] Optimistic-lock conflict detected; running the onLockDetected handler", e);
            onLockDetected.run();
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
    public void doAfterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            log.debug("[Transaction] No active transaction; running doAfterCommit action immediately");
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    log.debug("[Transaction] Running action after commit");
                    action.run();
                }
            }
        });
    }

    @Override
    public void doAfterRollback(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // No transaction means no rollback to react to — deliberately a no-op.
            log.debug("[Transaction] No active transaction; doAfterRollback is a no-op");
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    log.debug("[Transaction] Running action after rollback");
                    action.run();
                }
            }
        });
    }

    private TransactionTemplate template(boolean readOnly) {
        return readOnly ? readOnlyTransactionTemplate : transactionTemplate;
    }
}
