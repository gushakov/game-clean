package com.github.gameclean.infrastructure.transaction;

import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.transaction.TransactionOperationsError;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SpringTransactionAdapter}'s failure-currency behaviour — the failure modes the
 * adapter must keep strictly apart, plus the optimistic-lock handler of the {@code (action, onLockDetected)}
 * overload. A real {@link TransactionTemplate} is driven over a <em>mock</em> {@link PlatformTransactionManager},
 * so the template's genuine exception semantics (propagate-and-rollback) are exercised rather than stubbed away.
 *
 * <ul>
 *   <li>A failure of the transaction <em>machinery</em> (begin/commit) is a Spring {@code TransactionException}
 *       and must be wrapped into the port's {@link TransactionOperationsError}.</li>
 *   <li>A {@link PersistenceOperationsError} thrown by the <em>action</em> is already a port type: it must
 *       pass through untouched (no double-wrap) and still trigger the rollback.</li>
 *   <li>An {@link OptimisticLockingError} from the <em>action</em> is routed to the handler when one is
 *       supplied (the transaction having rolled back), and propagates unchanged when the handler is
 *       {@code null} — the "in addition, not a replacement" contract.</li>
 * </ul>
 */
class SpringTransactionAdapterTest {

    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final SpringTransactionAdapter adapter = new SpringTransactionAdapter(
            new TransactionTemplate(txManager), new TransactionTemplate(txManager));

    @Test
    void wrapsAFailureToBeginTheTransactionIntoThePortType() {
        when(txManager.getTransaction(any())).thenThrow(new CannotCreateTransactionException("no connection"));

        assertThatThrownBy(() -> adapter.doInTransaction(false, () -> { /* never reached */ }))
                .isInstanceOf(TransactionOperationsError.class)
                .hasCauseInstanceOf(CannotCreateTransactionException.class);
    }

    @Test
    void wrapsAFailureToBeginInTheResultReturningOverloadToo() {
        when(txManager.getTransaction(any())).thenThrow(new CannotCreateTransactionException("no connection"));

        assertThatThrownBy(() -> adapter.doInTransactionWithResult(false, () -> "unreached"))
                .isInstanceOf(TransactionOperationsError.class)
                .hasCauseInstanceOf(CannotCreateTransactionException.class);
    }

    @Test
    void letsTheActionsOwnPersistenceErrorPassThroughUntouchedAndRollsBack() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        PersistenceOperationsError fromAction = new PersistenceOperationsError("database unavailable");

        // The action's port exception must arrive at the caller as itself — never re-wrapped in a
        // TransactionOperationsError — and the transaction must have been rolled back.
        assertThatThrownBy(() -> adapter.doInTransaction(false, () -> { throw fromAction; }))
                .isSameAs(fromAction);
        verify(txManager).rollback(any());
    }

    @Test
    void routesAnOptimisticLockLossToTheHandlerInsteadOfRethrowing() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        AtomicBoolean handlerRan = new AtomicBoolean(false);

        // The action's versioned write loses the race: the OptimisticLockingError rolls the transaction back
        // and must be handed to the handler (an expected concurrency outcome), never surfacing to the caller.
        adapter.doInTransaction(
                () -> { throw new OptimisticLockingError("stale version"); },
                () -> handlerRan.set(true));

        assertThat(handlerRan).isTrue();
        verify(txManager).rollback(any());
    }

    @Test
    void propagatesTheOptimisticLockLossWhenNoHandlerIsGiven() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        OptimisticLockingError lost = new OptimisticLockingError("stale version");

        // A null handler is the "in addition, not a replacement" path: the error propagates (rolling back) to
        // the use case's outermost checkpoint exactly as the plain overloads let it.
        assertThatThrownBy(() -> adapter.doInTransaction(() -> { throw lost; }, null))
                .isSameAs(lost);
        verify(txManager).rollback(any());
    }
}
