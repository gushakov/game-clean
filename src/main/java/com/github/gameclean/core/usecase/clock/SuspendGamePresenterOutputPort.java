package com.github.gameclean.core.usecase.clock;

/**
 * Presenter (driven) output port for {@code SuspendGame}, co-located with its use case. Extends
 * {@link ClockReadinessPresenterOutputPort} (and thereby the catch-all {@code presentError}), inheriting the
 * shared {@code presentGameNotInitialized} readiness outcome and adding only this use case's success.
 *
 * <p>One success outcome, {@link #presentGameSuspended()} — a parting acknowledgement that the session's
 * time was banked. It carries no data: the player is leaving, and the banked total is an internal figure, not
 * something to read back. It is presented <em>after the bank commits</em>, so the player is never told their
 * time was saved before it durably was.
 */
public interface SuspendGamePresenterOutputPort extends ClockReadinessPresenterOutputPort {

    /** Happy path: the session's elapsed time has been banked into the world clock; the player may leave. */
    void presentGameSuspended();
}
