package com.github.gameclean.core.usecase.npc;

import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code Wander}, co-located with its use case. Extends
 * {@link ErrorHandlingPresenterOutputPort} for the catch-all. Two outcomes, both deliberately present so
 * "exactly one {@code present*} per run" holds even for the common quiet wander (the invariant mirrors the old
 * animate presenter, now split out here):
 *
 * <ul>
 *   <li>{@link #presentNpcMovement(PerceivedNpcMovement)} — the wander was perceptible: a departure from, or an
 *       arrival into, the player's current scene. An <em>asynchronous</em> presentation (a system actor
 *       producing output the player sees mid-session, rendered above the live prompt).</li>
 *   <li>{@link #presentNothingHappened()} — the common case: the wander happened off-stage (neither its source
 *       nor its target is the player's scene), or the execution was a quiet no-op (NPC/scene/exit gone, or a
 *       lost lock race). A real outcome the adapter renders as silence (a trace log), not a missing presentation.</li>
 * </ul>
 *
 * <p>Unlike the old animate presenter this carries <em>one</em> movement, not a list: the policy dispatches one
 * wander command per NPC, so each {@code Wander} execution narrates a single movement.
 */
public interface WanderPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * A wander the player can witness from their current scene: narrate it above the prompt.
     *
     * @param movement the perceptible movement (a departure or an arrival)
     */
    void presentNpcMovement(PerceivedNpcMovement movement);

    /**
     * Quiet wander: it happened off-stage, or the execution was a no-op (gone NPC/scene/exit, or a lost lock
     * race) — nothing to narrate.
     */
    void presentNothingHappened();
}
