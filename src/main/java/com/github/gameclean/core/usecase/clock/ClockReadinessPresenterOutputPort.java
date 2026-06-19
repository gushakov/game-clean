package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Shared presenter (driven) outcome for the clock-reading use cases: the game is <b>not yet in a playable
 * state</b> because its clock has not been initialized. {@code AskForTime} and {@code SuspendGame} both read
 * the persisted clock and both must report its absence, so the outcome is factored here and their presenter
 * ports extend this one — the same interface-extension mechanism that lets {@code look}/{@code move} share
 * the {@code OrientPlayerPresenterOutputPort} cluster.
 *
 * <p><b>A sibling readiness cluster, not a unified gate.</b> "Is the game initialized?" is deliberately
 * <em>not</em> one cross-cutting outcome: a use case only ever checks the precondition on the state it
 * actually reads (the {@code orient} prologue checks the player and current scene; the clock use cases check
 * the clock), so readiness lives as small clusters keyed to that shared sub-state, each extending
 * {@link ErrorHandlingPresenterOutputPort} directly — never a single {@code presentGameNotInitialized} gate
 * no interaction can honestly evaluate, and no common readiness super-interface above the clusters until one
 * is genuinely shared (design-notes §4, thread #2).
 */
public interface ClockReadinessPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Precondition outcome: the game clock has not been initialized, so the world is not yet in a playable
     * state. Reached by branch-and-present (then {@code return}) when the clock is absent — not by throwing.
     */
    void presentGameNotInitialized();
}
