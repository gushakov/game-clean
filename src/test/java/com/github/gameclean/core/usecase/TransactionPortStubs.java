package com.github.gameclean.core.usecase;

import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;

import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

/**
 * Shared Mockito stubs for the {@link TransactionOperationsOutputPort} mock used by read-write use case tests.
 * Each method runs the transactional action inline so a use case under test behaves as it would against the
 * real adapter: the work happens synchronously, any error the action throws propagates, and the presentation
 * that follows the block is observable in the test body.
 *
 * <p>One stub per demarcation overload, each serving the success and error tests alike — there is no
 * after-commit hook to fire, since every interaction presents as a plain statement after the call:
 * <ul>
 *   <li>{@code (boolean, Runnable)} — the plain demarcation ({@code Move}, {@code Drop}, {@code SuspendGame}):
 *       {@link #runTransaction};</li>
 *   <li>{@code (boolean, Supplier)} — the plain demarcation returning what the block settled
 *       ({@code InitializeGame}): {@link #runTransactionWithResult};</li>
 *   <li>{@code (Supplier, T onLockDetected)} — the result-returning lock-aware demarcation ({@code Take},
 *       {@code TakeFrom}, {@code FightNpc}, {@code Wander}, {@code AnnounceTimeOfDay}):
 *       {@link #runLockAwareTransactionWithResult}.</li>
 * </ul>
 */
public final class TransactionPortStubs {

    private TransactionPortStubs() {
    }

    /** {@code (boolean, Runnable)} overload: run the action inline, letting any error it throws propagate. */
    public static void runTransaction(TransactionOperationsOutputPort txOps) {
        doAnswer(inv -> {
            inv.getArgument(1, Runnable.class).run();
            return null;
        }).when(txOps).doInTransaction(anyBoolean(), any(Runnable.class));
    }

    /**
     * {@code (boolean, Supplier)} overload: run the action inline and return its result, letting any error it
     * throws propagate.
     */
    public static void runTransactionWithResult(TransactionOperationsOutputPort txOps) {
        doAnswer(inv -> inv.getArgument(1, Supplier.class).get())
                .when(txOps).doInTransactionWithResult(anyBoolean(), any(Supplier.class));
    }

    /**
     * {@code (Supplier, T)} result-returning lock-aware overload, mirroring the adapter on both paths: run the
     * action inline and return its result; if it raises {@link OptimisticLockingError}, return the supplied
     * loss value instead. Any other error propagates, as the real port lets it.
     */
    public static void runLockAwareTransactionWithResult(TransactionOperationsOutputPort txOps) {
        doAnswer(inv -> {
            try {
                return inv.getArgument(0, Supplier.class).get();
            } catch (OptimisticLockingError e) {
                return inv.getArgument(1);
            }
        }).when(txOps).doInTransactionWithResult(any(Supplier.class), any());
    }
}
