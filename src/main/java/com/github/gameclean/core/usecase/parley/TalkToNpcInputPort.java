package com.github.gameclean.core.usecase.parley;

import java.util.List;

/**
 * Driving (input) port for the <b>TalkToNpc</b> user goal: the player addresses an NPC standing in their
 * current scene and the NPC answers. The primary actor is the <em>player</em>; the NPC is a passive party — it
 * initiates nothing here (an NPC that <em>speaks first</em> would be a secondary-actor step, dispatched by the
 * animate policy like a counterstrike; not this goal).
 *
 * <p>Like {@code hit}, a two-interaction designation converging on one outcome — the player names the NPC by
 * description or, when that is ambiguous, by choosing from a numbered menu:
 *
 * <ul>
 *   <li>{@link #playerTalksToTarget(String)} — designate by a descriptive fragment (the disambiguation lives in
 *       the {@code select} subcase: no match, exactly one, or a menu offered).</li>
 *   <li>{@link #playerTalksToChosenCandidate(int, List)} — designation by choosing from the last-offered
 *       candidates, completing the disambiguation.</li>
 * </ul>
 *
 * <p>The outcome stripes resolve synchronously and are reported through {@link TalkToNpcPresenterOutputPort};
 * every method is {@code void}. Primitives cross the boundary; the selection interaction takes the offered
 * tokens as a value (dependency rejection), exactly as {@code take} and {@code hit} do.
 */
public interface TalkToNpcInputPort {

    /**
     * The player addresses an NPC <em>by description</em>. Resolves the living NPCs in the player's current
     * scene and either talks to the single match, reports nothing-matches, or — when the fragment is
     * ambiguous — offers the candidates to disambiguate.
     *
     * @param target the player's free-text fragment (non-blank; the driving adapter supplies the trimmed
     *               remainder of the command line, prepositions stripped)
     */
    void playerTalksToTarget(String target);

    /**
     * The player addresses an NPC <em>by choosing from the candidates last offered</em> — the completion of the
     * disambiguation flow. The pick is resolved against the offered tokens (out of range → no such option) and
     * re-validated against live scene state (gone → no longer here) before the NPC answers.
     *
     * @param ordinal       the 1-based menu number the player picked
     * @param offeredTokens the candidate id tokens last offered, in display order — supplied as a value by the
     *                      driving adapter, empty when no offer is pending
     */
    void playerTalksToChosenCandidate(int ordinal, List<String> offeredTokens);
}
