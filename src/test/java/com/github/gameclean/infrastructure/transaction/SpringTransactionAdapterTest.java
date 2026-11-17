package com.github.gameclean.infrastructure.transaction;

import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.transaction.TransactionOperationsError;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
 *   <li>The same loss, through the result-returning {@code (action, onLockDetected)} overload, comes back as
 *       the caller's loss <em>value</em>; every other error still propagates, and the block refuses to join
 *       an active transaction (see {@link LockAwareBlockMustBeOutermost}).</li>
 * </ul>
 *
 * <p>The after-commit hook's fail-loud contract is pinned separately in {@link AfterCommitFailsLoudly}, which
 * needs a <em>real</em> transaction manager: a mocked one never fires synchronizations at all.
 */
class SpringTransactionAdapterTest {

    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final SpringTransactionAdapter adapter = adapterOver(txManager);

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

    @Test
    void returnsTheActionsResultWhenTheLockAwareTransactionCommits() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        String settled = adapter.doInTransactionWithResult(() -> "committed", "lost");

        assertThat(settled).isEqualTo("committed");
        verify(txManager).commit(any());
    }

    @Test
    void returnsTheLossValueWhenTheVersionedWriteLosesTheRace() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        // The loss is an expected concurrency outcome: it rolls the transaction back and comes back as the
        // caller's own value, never surfacing as an OptimisticLockingError.
        String settled = adapter.doInTransactionWithResult(
                () -> { throw new OptimisticLockingError("stale version"); },
                "lost");

        assertThat(settled).isEqualTo("lost");
        verify(txManager).rollback(any());
    }

    @Test
    void letsAnyOtherErrorOfTheLockAwareActionPassThroughUntouchedAndRollsBack() {
        when(txManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        PersistenceOperationsError fromAction = new PersistenceOperationsError("database unavailable");

        // Only the lock loss is turned into a value. A fault stays a fault: it reaches the caller as itself.
        assertThatThrownBy(() -> adapter.doInTransactionWithResult(() -> { throw fromAction; }, "lost"))
                .isSameAs(fromAction);
        verify(txManager).rollback(any());
    }

    @Test
    void wrapsAFailureToBeginInTheLockAwareResultOverloadToo() {
        when(txManager.getTransaction(any())).thenThrow(new CannotCreateTransactionException("no connection"));

        assertThatThrownBy(() -> adapter.doInTransactionWithResult(() -> "unreached", "lost"))
                .isInstanceOf(TransactionOperationsError.class)
                .hasCauseInstanceOf(CannotCreateTransactionException.class);
    }

    @Test
    void rejectsANullLossValue() {
        // A null loss value would make "lost the race" indistinguishable from an action returning null: a
        // caller programming error, refused before any transaction is opened.
        assertThatThrownBy(() -> adapter.doInTransactionWithResult(() -> "unreached", null))
                .isInstanceOf(NullPointerException.class);
        verifyNoInteractions(txManager);
    }

    /**
     * The lock-aware result overload must be the outermost transaction. This needs the <em>real</em> manager
     * of {@link AfterCommitFailsLoudly} for the same reason: a mocked one never marks a transaction active.
     */
    @Nested
    class LockAwareBlockMustBeOutermost {

        private final SpringTransactionAdapter adapter = adapterOver(new NoOpTransactionManager());

        @Test
        void refusesToRunInsideAnActiveTransactionBeforeTheActionRuns() {
            AtomicBoolean actionRan = new AtomicBoolean(false);

            // Joined to the outer block, a swallowed loss would return the loss value while the outer
            // transaction is already rollback-only. The adapter refuses on every run, lost race or not.
            assertThatThrownBy(() -> adapter.doInTransaction(false, () ->
                    adapter.doInTransactionWithResult(() -> {
                        actionRan.set(true);
                        return "committed";
                    }, "lost")))
                    .isInstanceOf(TransactionOperationsError.class)
                    .hasMessageContaining("outermost");

            assertThat(actionRan).as("the joined action never ran").isFalse();
        }

        @Test
        void runsAsTheOutermostTransaction() {
            assertThat(adapter.doInTransactionWithResult(() -> "committed", "lost")).isEqualTo("committed");
        }
    }

    /**
     * The after-commit hook's fail-loud contract: <em>whatever the deferred action throws, the caller sees</em>.
     *
     * <p>These tests cannot use the mocked {@link PlatformTransactionManager} of the enclosing class — a mock
     * never initializes {@link TransactionSynchronizationManager} and so never fires synchronizations at all,
     * which is precisely why this hazard survived an otherwise well-tested adapter. They drive a minimal real
     * {@link AbstractPlatformTransactionManager} with no-op resources instead, so registration, commit and the
     * synchronization callbacks are Spring's own.
     *
     * <p>What they pin is the difference between {@code afterCommit()} and {@code afterCompletion(int)}: the
     * latter runs inside a {@code catch (Throwable)} that logs and carries on, so every assertion here would
     * fail (silently, in production) were the adapter to use it.
     */
    @Nested
    class AfterCommitFailsLoudly {

        private final SpringTransactionAdapter adapter = adapterOver(new NoOpTransactionManager());

        @Test
        void letsAThrowingDeferredActionReachTheCallerOfDoInTransaction() {
            IllegalStateException fromDeferredAction = new IllegalStateException("presenter blew up after commit");

            // The transaction has committed by then, so this is not a demarcation fault: the error must arrive
            // as itself, never wrapped into TransactionOperationsError by the adapter's narrow catch.
            assertThatThrownBy(() -> adapter.doInTransaction(false, () -> adapter.doAfterCommit(() -> {
                throw fromDeferredAction;
            }))).isSameAs(fromDeferredAction);
        }

        @Test
        void skipsTheCallbacksQueuedBehindAThrowingOne() {
            AtomicBoolean laterCallbackRan = new AtomicBoolean(false);

            // Why statement order inside a deferred block is load-bearing: a throwing presenter cancels the
            // callbacks registered after it, so an external signal must be emitted before the presentation.
            assertThatThrownBy(() -> adapter.doInTransaction(false, () -> {
                adapter.doAfterCommit(() -> { throw new IllegalStateException("first callback fails"); });
                adapter.doAfterCommit(() -> laterCallbackRan.set(true));
            })).isInstanceOf(IllegalStateException.class);

            assertThat(laterCallbackRan).as("callback queued behind the throwing one was skipped").isFalse();
        }

        @Test
        void honoursTheSameContractOnTheImmediateNoTransactionBranch() {
            IllegalStateException fromImmediateAction = new IllegalStateException("presenter blew up immediately");

            // With no transaction active the action runs inline — one contract for both branches, so the caller
            // of doAfterCommit itself sees the error.
            assertThatThrownBy(() -> adapter.doAfterCommit(() -> { throw fromImmediateAction; }))
                    .isSameAs(fromImmediateAction);
        }
    }

    private static SpringTransactionAdapter adapterOver(PlatformTransactionManager manager) {
        return new SpringTransactionAdapter(new TransactionTemplate(manager), new TransactionTemplate(manager));
    }

    /**
     * The smallest transaction manager that is still <em>real</em>: {@link AbstractPlatformTransactionManager}
     * supplies the synchronization lifecycle (activate on begin, trigger on commit, clear on completion), while
     * every resource-level operation here is a no-op because no database is involved.
     */
    private static class NoOpTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            // no resource to bind
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            // nothing to commit; the synchronization callbacks are what these tests exercise
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            // nothing to roll back
        }
    }
}
