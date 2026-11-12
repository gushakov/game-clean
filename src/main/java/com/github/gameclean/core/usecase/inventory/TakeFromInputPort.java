package com.github.gameclean.core.usecase.inventory;

import java.util.List;

/**
 * Driving (input) port for the <b>TakeFrom</b> user goal: the player takes one specific thing <em>out of a
 * container</em> lying in their current scene — loot off a corpse, the contents of a chest — into their
 * keeping. The contained-item sibling of {@code Take}: same destination (the player's keeping), different
 * provenance, and so a different designation — the command names <em>two</em> things, the item and the
 * container it should come from.
 *
 * <p><b>The container fragment is a filter, not a second dialogue.</b> It narrows <em>where to look</em>: every
 * container on the ground here that the fragment designates is searched, and the item fragment is matched over
 * the union of their contents. So there is exactly one thing the player can be asked to disambiguate — the
 * item — and one menu, however many look-alike containers (two corpses of the same wanderer, say) lie about:
 *
 * <ul>
 *   <li>{@link #playerTakesTargetFrom(String, String)} — the main success scenario: designate the item and its
 *       container by descriptive fragments. No container matches → its own outcome; otherwise the item is
 *       resolved inside {@code select} — nothing like it in there, exactly one (taken), or more than one
 *       (disambiguation menu offered).</li>
 *   <li>{@link #playerTakesChosenContent(int, List)} — designation by choosing from the contents last offered,
 *       completing the disambiguation.</li>
 * </ul>
 *
 * <p><b>Concurrency.</b> An item inside a container on the ground is as contested as one lying beside it — any
 * actor in the scene can reach in — so the write is version-checked and a lost race is presented as
 * {@code presentItemGotAway}, exactly as for {@code Take}.
 *
 * <p>Both methods are {@code void}: every outcome is reported through {@link TakeFromPresenterOutputPort},
 * never returned. The driving adapter hands the offered tokens in as a value (dependency rejection).
 */
public interface TakeFromInputPort {

    /**
     * The player designates a thing to take out of a container, both <em>by description</em>.
     *
     * @param itemTarget      the player's free-text fragment for the thing to take (non-blank)
     * @param containerTarget the player's free-text fragment for the container(s) to look in (non-blank)
     */
    void playerTakesTargetFrom(String itemTarget, String containerTarget);

    /**
     * The player designates a thing to take <em>by choosing from the contents last offered</em> — the
     * completion of the disambiguation flow. The pick is resolved against the offered tokens (out of range →
     * no such option) and re-validated against live state — the chosen item must still lie inside a container
     * on the ground of the player's current scene — before it is taken.
     *
     * @param ordinal                the 1-based menu number the player picked
     * @param offeredCandidateTokens the candidate id tokens last offered, in display order — supplied as a
     *                               value by the driving adapter
     */
    void playerTakesChosenContent(int ordinal, List<String> offeredCandidateTokens);
}
