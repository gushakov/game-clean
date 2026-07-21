package com.github.gameclean.core.usecase.clock;

import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code SuspendGame}, co-located with its use case. It declares exactly
 * the outcomes this use case presents: its success, the clock-readiness precondition it checks inline, and
 * the inherited catch-all — the same per-port declaration as {@code AskForTime} and
 * {@code AnnounceTimeOfDay}, with only the rendering shared in the adapter.
 *
 * <p>One success outcome, {@link #presentGameSuspended()} — a parting acknowledgement that the session's
 * time was banked. It carries no data: the player is leaving, and the banked total is an internal figure, not
 * something to read back. It is presented <em>after the bank commits</em>, so the player is never told their
 * time was saved before it durably was.
 */
public interface SuspendGamePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Precondition outcome: the game clock has not been initialized, so the world is not yet in a playable
     * state. Reached by branch-and-present (then {@code return}) when the clock is absent — not by throwing.
     */
    void presentGameNotInitialized();

    /** Happy path: the session's elapsed time has been banked into the world clock; the player may leave. */
    void presentGameSuspended();
}
