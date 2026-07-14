package com.github.gameclean.core.usecase.npc;

import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

import java.util.List;

/**
 * Presenter (driven) output port for {@code AnimateNpcs}, co-located with its use case. Extends
 * {@link ErrorHandlingPresenterOutputPort} for the catch-all {@code presentError}. Every method is {@code void}
 * and follows the {@code present + Outcome} grammar; domain objects pass straight through (immutable, so the
 * presenter cannot corrupt them — no Response-Model DTOs).
 *
 * <p>Two outcomes, both deliberately present so "exactly one {@code present*} per run" holds even for a quiet
 * tick (the use case never silently returns without presenting), mirroring {@code AnnounceTimeOfDay}'s
 * quiet-poll outcome:
 * <ul>
 *   <li>{@link #presentNpcMovements(List)} — one or more NPC movements the player can witness (departures from,
 *       or arrivals into, their current scene). This is an <em>asynchronous</em> presentation: a system actor
 *       producing output the player sees mid-session (rendered with JLine {@code printAbove}).</li>
 *   <li>{@link #presentNothingHappened()} — the common case: no NPCs, no NPC moved, or no movement was
 *       perceptible from where the player stands. A real outcome the adapter renders as silence (a trace log),
 *       not a missing presentation. It also covers the ticker firing before the world is seeded (no NPCs yet).</li>
 * </ul>
 */
public interface AnimateNpcsPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * One or more NPC movements the player can witness from their current scene: narrate them to the player.
     *
     * @param movements the perceptible movements this tick, in the order the NPCs were enumerated (non-empty)
     */
    void presentNpcMovements(List<PerceivedNpcMovement> movements);

    /**
     * Quiet tick: no NPCs, none moved, or nothing was perceptible from where the player stands — nothing to
     * narrate. Also the safe pre-initialization outcome (the ticker firing before any NPC is seeded).
     */
    void presentNothingHappened();
}
