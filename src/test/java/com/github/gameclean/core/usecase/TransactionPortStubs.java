package com.github.gameclean.core.usecase;

import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

/**
 * Shared Mockito stubs for the {@link TransactionOperationsOutputPort} mock used by read-write use case tests.
 * Each method runs the transactional action inline so a use case under test behaves as it would against the
 * real adapter — the work happens synchronously and deferred presentations are observable in the test body.
 *
 * <p>The port has two {@code doInTransaction} overloads, so the helpers come in two families:
 * <ul>
 *   <li>the {@code (boolean commit, Runnable action)} overload — the plain demarcation used by most
 *       interactions ({@code Move}, {@code SuspendGame}, {@code InitializeGame});</li>
 *   <li>the {@code (Runnable action, Runnable onLockDetected)} overload — the optimistic-lock-aware
 *       demarcation used by {@code AnnounceTimeOfDay}.</li>
 * </ul>
 *
 * <p>Whether {@link #fireAfterCommit} is stubbed is deliberate, not incidental: under Mockito strict stubs an
 * after-commit stub that never runs (e.g. an error path that aborts the transaction) is flagged as
 * unnecessary. So success-path tests use the {@code *AndFireAfterCommit} variant, while error / lock-loss
 * tests stub only {@code doInTransaction}.
 */
public final class TransactionPortStubs {

    private TransactionPortStubs() {
    }

    /**
     * {@code (boolean, Runnable)} overload: run the action inline, letting any error it throws propagate as the
     * real port does. After-commit is not stubbed — use on error paths.
     */
    public static void runTransaction(TransactionOperationsOutputPort txOps) {
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        }).when(txOps).doInTransaction(anyBoolean(), any(Runnable.class));
    }

    /** {@code (boolean, Runnable)} overload: run the action inline and fire after-commit callbacks immediately. */
    public static void runTransactionAndFireAfterCommit(TransactionOperationsOutputPort txOps) {
        runTransaction(txOps);
        fireAfterCommit(txOps);
    }

    /**
     * {@code (Runnable, Runnable)} lock-aware overload: run the action inline (no lock detected) and fire
     * after-commit callbacks immediately.
     */
    public static void runLockAwareTransactionAndFireAfterCommit(TransactionOperationsOutputPort txOps) {
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(txOps).doInTransaction(any(Runnable.class), any(Runnable.class));
        fireAfterCommit(txOps);
    }

    /**
     * {@code (Runnable, Runnable)} lock-aware overload, mirroring the adapter's lock path: run the action; if it
     * raises {@link OptimisticLockingError}, run the lock-detected handler instead. After-commit is not stubbed.
     */
    public static void runLockAwareTransactionDetectingLock(TransactionOperationsOutputPort txOps) {
        doAnswer(inv -> {
            try {
                inv.getArgument(0, Runnable.class).run();
            } catch (OptimisticLockingError e) {
                inv.getArgument(1, Runnable.class).run();
            }
            return null;
        }).when(txOps).doInTransaction(any(Runnable.class), any(Runnable.class));
    }

    /** Fire after-commit callbacks immediately, as a committed transaction would. */
    public static void fireAfterCommit(TransactionOperationsOutputPort txOps) {
        doAnswer(inv -> {
            inv.getArgument(0, Runnable.class).run();
            return null;
        }).when(txOps).doAfterCommit(any(Runnable.class));
    }
}
