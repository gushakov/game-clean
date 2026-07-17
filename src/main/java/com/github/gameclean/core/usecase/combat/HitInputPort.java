package com.github.gameclean.core.usecase.combat;

import java.util.List;

/**
 * Driving (input) port for the <b>Hit</b> user goal: the player strikes one specific NPC in their current
 * scene. Like {@code take}, a <b>two-interaction</b> use case — the player designates the target either by
 * description or, when that is ambiguous, by choosing from a numbered menu — and it <em>writes</em>: the
 * struck NPC's hit points are lowered.
 *
 * <p>Both interactions are initiated by the <em>player</em>, both open with the shared {@code orient} prologue
 * and resolve the target through the shared {@code select} subcase (bound to an NPC-in-scene candidate), and
 * both converge on the same strike. They differ only in how the player <b>designates</b> the target (the
 * Cockburn variation the {@code select} subcase owns):
 *
 * <ul>
 *   <li>{@link #playerHitsTarget(String)} — the main success scenario: designate by a descriptive fragment.
 *       Branches three ways inside {@code select} — no match, exactly one (struck), or more than one
 *       (disambiguation menu offered).</li>
 *   <li>{@link #playerHitsChosenCandidate(int, List)} — designation by choosing from the candidates last
 *       offered, completing the disambiguation.</li>
 * </ul>
 *
 * <p><b>Concurrency.</b> An NPC is now a contested aggregate — the player's strike and the wandering ticker
 * both write it — so this is a select-then-mutate where two actors can race. {@code select}'s
 * re-provision-and-confirm only narrows the window; the authoritative close is the NPC's optimistic-locking
 * version, and a lost race is presented as {@code presentNpcGotAway} (the write-side twin of {@code select}'s
 * read-side {@code presentTargetNoLongerAvailable}).
 *
 * <p>The outcome stripes — struck (survived) / slain (hit points reach zero) / got away (lost race) — resolve
 * synchronously within the interaction (see the package doc). Both methods are {@code void}: every outcome is
 * reported through {@link HitPresenterOutputPort}, never returned. The driving adapter hands the offered
 * tokens in as a value (dependency rejection), exactly as for {@code take}.
 */
public interface HitInputPort {

    /**
     * The player designates an NPC to strike <em>by description</em>. Resolves the living NPCs in the player's
     * current scene and either strikes the single match, reports nothing-matches, or — when the fragment is
     * ambiguous — offers the candidates to disambiguate.
     *
     * @param target the player's free-text fragment (non-blank; the driving adapter supplies the trimmed
     *               remainder of the command line)
     */
    void playerHitsTarget(String target);

    /**
     * The player designates an NPC to strike <em>by choosing from the candidates last offered</em> — the
     * completion of the disambiguation flow. The pick is resolved against the offered tokens (out of range →
     * no such option) and re-validated against live scene state before the strike (gone → no longer here; lost
     * race at commit → got away).
     *
     * @param ordinal       the 1-based menu number the player picked
     * @param offeredTokens the candidate id tokens last offered, in display order — supplied as a value by the
     *                      driving adapter, empty when no offer is pending
     */
    void playerHitsChosenCandidate(int ordinal, List<String> offeredTokens);
}
