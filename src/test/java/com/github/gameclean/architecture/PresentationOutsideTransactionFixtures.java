package com.github.gameclean.architecture;

import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;

import java.util.Optional;

/**
 * Throwaway use cases for {@link PresentationOutsideTransactionRuleTest}: each nested class is one way of
 * presenting around a transaction block, imported on its own and held against the rule of
 * {@link PresentationOutsideTransactionArchitectureTest}. They are never instantiated. Several violate the rule
 * on purpose, which is why that rule analyzes production classes only.
 */
final class PresentationOutsideTransactionFixtures {

    private PresentationOutsideTransactionFixtures() {
    }

    private enum Attempt { DONE, LOST }

    /** The contract: the block holds the write, the presentation is the statement after it. */
    static final class PresentsAfterTheBlock {
        TransactionOperationsOutputPort txOps;
        ErrorHandlingPresenterOutputPort presenter;

        void interaction(Runnable write) {
            Attempt attempt = txOps.doInTransactionWithResult(() -> {
                write.run();
                return Attempt.DONE;
            }, Attempt.LOST);
            if (attempt == Attempt.LOST) {
                presenter.presentError(new IllegalStateException("lost"));
            }
        }
    }

    /** A presentation inside the lambda handed to the block. */
    static final class PresentsInsideTheBlock {
        TransactionOperationsOutputPort txOps;
        ErrorHandlingPresenterOutputPort presenter;

        void interaction(Runnable write) {
            txOps.doInTransaction(false, () -> {
                write.run();
                presenter.presentError(new IllegalStateException("too early"));
            });
        }
    }

    /** A presenter method handed to the block as a method reference: no synthetic method, matched as such. */
    static final class PresentsThroughAMethodReference {
        TransactionOperationsOutputPort txOps;
        PresentsNothingPresenterOutputPort presenter;

        void interaction() {
            txOps.doInTransaction(false, presenter::presentNothing);
        }
    }

    /** A private helper that presents, called from inside the block: the rule does not follow the call. */
    static final class PresentsThroughAHelperInsideTheBlock {
        TransactionOperationsOutputPort txOps;
        ErrorHandlingPresenterOutputPort presenter;

        void interaction(Runnable write) {
            txOps.doInTransaction(false, () -> {
                write.run();
                present();
            });
        }

        private void present() {
            presenter.presentError(new IllegalStateException("hidden"));
        }
    }

    /** A class without the transaction port is out of the rule's scope, lambda or not. */
    static final class PresentsFromALambdaWithoutTransactionPort {
        ErrorHandlingPresenterOutputPort presenter;

        void interaction(Optional<Exception> failure) {
            failure.ifPresent(e -> presenter.presentError(e));
        }
    }

    interface PresentsNothingPresenterOutputPort {
        void presentNothing();
    }
}
