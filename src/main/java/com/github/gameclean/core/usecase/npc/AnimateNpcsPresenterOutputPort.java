package com.github.gameclean.core.usecase.npc;

import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code AnimateNpcs}, co-located with its use case. Extends
 * {@link ErrorHandlingPresenterOutputPort} for the catch-all {@code presentError}.
 *
 * <p><b>The policy has a single, always-quiet stripe.</b> Since {@code AnimateNpcs} became a read-only policy
 * that <em>dispatches</em> commands (issue #66 step 2), it no longer narrates anything itself — each dispatched
 * action narrates its own outcome mid-run, as its own executing interaction (a wander through {@code Wander},
 * a strike through {@code FightNpc}), so anything the policy presented would read out of order. Its own outcome
 * is therefore always {@link #presentNothingHappened()}: a real outcome the adapter renders as silence (a trace
 * log), not a missing presentation — so "exactly one {@code present*} per run" holds for every tick. It also
 * covers the ticker firing before the world is seeded (no NPCs yet). The witnessed-movement narration this port
 * once carried moved to {@code Wander}'s presenter.
 */
public interface AnimateNpcsPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Quiet tick: the policy has decided and dispatched (possibly nothing) — nothing for the policy itself to
     * narrate. The executing interactions narrate their own outcomes.
     */
    void presentNothingHappened();
}
