package com.github.gameclean.infrastructure.transaction;

import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Default implementation of {@link TransactionOperationsOutputPort} over Spring's transaction SPI.
 *
 * <p>Two {@link TransactionTemplate}s are injected — a read-write one and a read-only one — so the
 * {@code readOnly} flag selects the right propagation/isolation without per-call configuration. A
 * runtime error thrown by an action propagates out of the template, which triggers Spring's default
 * rollback-on-runtime; the use case's outermost checkpoint then sees it.
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
public class SpringTransactionAdapter implements TransactionOperationsOutputPort {

    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;

    @Override
    public void doInTransaction(boolean readOnly, Runnable action) {
        log.debug("[Transaction] Running action in a transaction, readOnly={}", readOnly);
        template(readOnly).executeWithoutResult(status -> action.run());
    }

    @Override
    public <T> T doInTransactionWithResult(boolean readOnly, Supplier<T> action) {
        log.debug("[Transaction] Running action (with result) in a transaction, readOnly={}", readOnly);
        return template(readOnly).execute(status -> action.get());
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
