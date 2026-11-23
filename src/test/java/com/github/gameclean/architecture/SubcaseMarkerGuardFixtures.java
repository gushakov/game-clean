package com.github.gameclean.architecture;

import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;

/**
 * Throwaway parents for {@link SubcaseMarkerGuardRuleTest}: each nested class is one way of calling a subcase,
 * imported on its own and held against the rule of {@link SubcaseMarkerGuardArchitectureTest}. They are never
 * instantiated. Several violate the rule on purpose, which is why that rule analyzes production classes only.
 */
final class SubcaseMarkerGuardFixtures {

    private SubcaseMarkerGuardFixtures() {
    }

    /** The bug: the marker falls into the catch-all and an error is presented on top. */
    static final class CatchAllOnlyParent {
        OrientPlayerSubcaseInputPort orientPlayerSubcase;
        ErrorHandlingPresenterOutputPort presenter;

        void interaction() {
            try {
                orientPlayerSubcase.playerGetsBearings();
            } catch (Exception e) {
                presenter.presentError(e);
            }
        }
    }

    /** The contract: a dedicated catch ahead of the catch-all. */
    static final class GuardedParent {
        OrientPlayerSubcaseInputPort orientPlayerSubcase;
        ErrorHandlingPresenterOutputPort presenter;

        void interaction() {
            try {
                orientPlayerSubcase.playerGetsBearings();
            } catch (SubcaseAlreadyPresented e) {
                // already presented
            } catch (Exception e) {
                presenter.presentError(e);
            }
        }
    }

    /** No try block at all. */
    static final class BareParent {
        OrientPlayerSubcaseInputPort orientPlayerSubcase;

        void interaction() {
            orientPlayerSubcase.playerGetsBearings();
        }
    }

    /** The call sits in a lambda (a transaction callback, say) which a guarded try invokes. */
    static final class LambdaInsideGuardedTryParent {
        OrientPlayerSubcaseInputPort orientPlayerSubcase;
        ErrorHandlingPresenterOutputPort presenter;

        void interaction() {
            try {
                inCallback(() -> orientPlayerSubcase.playerGetsBearings());
            } catch (SubcaseAlreadyPresented e) {
                // already presented
            } catch (Exception e) {
                presenter.presentError(e);
            }
        }

        private static void inCallback(Runnable action) {
            action.run();
        }
    }

    /** The call sits in a private helper which a guarded try invokes. */
    static final class HelperInsideGuardedTryParent {
        OrientPlayerSubcaseInputPort orientPlayerSubcase;
        ErrorHandlingPresenterOutputPort presenter;

        void interaction() {
            try {
                opening();
            } catch (SubcaseAlreadyPresented e) {
                // already presented
            } catch (Exception e) {
                presenter.presentError(e);
            }
        }

        private void opening() {
            orientPlayerSubcase.playerGetsBearings();
        }
    }

    /** An inner catch-all gets the marker before the outer dedicated catch can. */
    static final class InnerCatchAllInsideGuardedTryParent {
        OrientPlayerSubcaseInputPort orientPlayerSubcase;
        ErrorHandlingPresenterOutputPort presenter;

        void interaction() {
            try {
                try {
                    orientPlayerSubcase.playerGetsBearings();
                } catch (Exception e) {
                    presenter.presentError(e);
                }
            } catch (SubcaseAlreadyPresented e) {
                // never reached for the call above
            }
        }
    }

    /** A subcase port of the fixtures' own, so that a caller can itself be a subcase. */
    interface NestingSubcaseInputPort {
        void playerDoesSomethingShared();
    }

    /** A subcase may let a nested subcase's marker propagate to its own parent. */
    static final class NestingSubcase implements NestingSubcaseInputPort {
        OrientPlayerSubcaseInputPort orientPlayerSubcase;

        @Override
        public void playerDoesSomethingShared() {
            orientPlayerSubcase.playerGetsBearings();
        }
    }

    /** A parent of the fixtures' own subcase, called through the port and unguarded. */
    static final class CatchAllOnlyParentOfNestingSubcase {
        NestingSubcaseInputPort nestingSubcase;
        ErrorHandlingPresenterOutputPort presenter;

        void interaction() {
            try {
                nestingSubcase.playerDoesSomethingShared();
            } catch (Exception e) {
                presenter.presentError(e);
            }
        }
    }
}
