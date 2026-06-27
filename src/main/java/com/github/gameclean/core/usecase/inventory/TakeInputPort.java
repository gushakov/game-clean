package com.github.gameclean.core.usecase.inventory;

import java.util.List;

/**
 * Driving (input) port for the <b>Take</b> user goal: the player picks one specific thing up off the ground in
 * their current scene into their keeping. Like {@code examine}, a <b>two-interaction</b> use case — the player
 * designates the target either by description or, when that is ambiguous, by choosing from a numbered menu —
 * but unlike {@code examine} it <em>writes</em>: the resolved item's location moves from the ground to the
 * player.
 *
 * <p>Both interactions are initiated by the <em>player</em>, both open with the shared {@code orient} prologue
 * and resolve the item through the shared {@code select} subcase, and both converge on the same success outcome
 * — {@code presentItemTaken}. They differ only in how the player <b>designates</b> the thing (the Cockburn
 * variation/extension structure the {@code select} subcase owns):
 *
 * <ul>
 *   <li>{@link #playerTakesTarget(String)} — the main success scenario: designate by a descriptive fragment.
 *       Branches three ways inside {@code select} — no match, exactly one (taken), or more than one
 *       (disambiguation menu offered).</li>
 *   <li>{@link #playerTakesChosenCandidate(int, List)} — designation by choosing from the candidates last
 *       offered, completing the disambiguation.</li>
 * </ul>
 *
 * <p><b>Concurrency.</b> A ground item is a contested resource — any actor in the scene can grab it — so this
 * is the project's first select-then-mutate where two actors can race. {@code select}'s re-provision-and-confirm
 * only narrows the window; the authoritative close is the item's optimistic-locking version, and a lost race is
 * presented as {@code presentItemGotAway} (the write-side twin of {@code select}'s read-side
 * {@code presentItemNoLongerHere}).
 *
 * <p>Both methods are {@code void}: every outcome is reported through
 * {@link TakePresenterOutputPort}, never returned. The driving adapter hands the offered tokens in as a value
 * (dependency rejection), exactly as for {@code examine}.
 */
public interface TakeInputPort {

    /**
     * The player designates a thing to take <em>by description</em>. Resolves the items on the ground in the
     * player's current scene and either takes the single match, reports nothing-matches, or — when the
     * fragment is ambiguous — offers the candidates to disambiguate.
     *
     * @param target the player's free-text fragment (non-blank; the driving adapter supplies the trimmed
     *               remainder of the command line)
     */
    void playerTakesTarget(String target);

    /**
     * The player designates a thing to take <em>by choosing from the candidates last offered</em> — the
     * completion of the disambiguation flow. The pick is resolved against the offered tokens (empty → nothing
     * offered; out of range → no such option) and re-validated against live scene state before the item is
     * taken (gone → no longer here; lost race at commit → got away).
     *
     * @param ordinal              the 1-based menu number the player picked
     * @param offeredCandidateTokens the candidate id tokens last offered, in display order — supplied as a value
     *                               by the driving adapter, empty when no offer is pending
     */
    void playerTakesChosenCandidate(int ordinal, List<String> offeredCandidateTokens);
}
