package com.github.gameclean.core.usecase.inventory;

import java.util.List;

/**
 * Driving (input) port for the <b>Drop</b> user goal: the player puts one specific thing they are carrying
 * down onto the ground of their current scene. The mirror image of {@code take} over the same
 * {@code orient}+{@code select} opening — like it, a <b>two-interaction</b> use case (designate the target by
 * description or, when that is ambiguous, by choosing from a numbered menu) that <em>writes</em>: the
 * resolved item's location moves from the player to the scene.
 *
 * <p>Both interactions are initiated by the <em>player</em>, both open with the shared {@code orient}
 * prologue and resolve the item through the inventory-sourced {@code select} subcase, and both converge on
 * the same success outcome — {@code presentItemDropped}:
 *
 * <ul>
 *   <li>{@link #playerDropsTarget(String)} — the main success scenario: designate by a descriptive fragment.
 *       Branches three ways inside {@code select} — no match, exactly one (dropped), or more than one
 *       (disambiguation menu offered).</li>
 *   <li>{@link #playerDropsChosenCandidate(int, List)} — designation by choosing from the candidates last
 *       offered, completing the disambiguation.</li>
 * </ul>
 *
 * <p><b>Concurrency — deliberately none of {@code take}'s handling.</b> A held item is single-writer today
 * (only its holder's own {@code drop} writes it), so unlike a contested ground item there is no race to
 * lose: the versioned save still guards integrity, but a lock loss is unreachable and would propagate to the
 * catch-all as the fault-like surprise it would be — the single-writer counterpart of {@code take}'s
 * contested-resource {@code onLockDetected}.
 *
 * <p>Both methods are {@code void}: every outcome is reported through {@link DropPresenterOutputPort}, never
 * returned. The driving adapter hands the offered tokens in as a value (dependency rejection), exactly as
 * for {@code examine}/{@code take}.
 */
public interface DropInputPort {

    /**
     * The player designates a carried thing to drop <em>by description</em>. Resolves the items in the
     * player's keeping and either drops the single match, reports nothing-matches, or — when the fragment is
     * ambiguous — offers the candidates to disambiguate.
     *
     * @param target the player's free-text fragment (non-blank; the driving adapter supplies the trimmed
     *               remainder of the command line)
     */
    void playerDropsTarget(String target);

    /**
     * The player designates a carried thing to drop <em>by choosing from the candidates last offered</em> —
     * the completion of the disambiguation flow. The pick is resolved against the offered tokens (out of
     * range → no such option) and re-validated against the player's live keeping (gone → no longer
     * available) before the item is dropped.
     *
     * @param ordinal                the 1-based menu number the player picked
     * @param offeredCandidateTokens the candidate id tokens last offered, in display order — supplied as a
     *                               value by the driving adapter, empty when no offer is pending
     */
    void playerDropsChosenCandidate(int ordinal, List<String> offeredCandidateTokens);
}
