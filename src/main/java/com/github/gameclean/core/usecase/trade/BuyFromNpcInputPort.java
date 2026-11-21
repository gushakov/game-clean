package com.github.gameclean.core.usecase.trade;

import java.util.List;

/**
 * Driving (input) port for the <b>BuyFromNpc</b> user goal: the player names a ware offered by an NPC
 * standing in their current scene, pays its price in coins from their own keeping, and receives the ware in
 * their hands. The primary actor is the <em>player</em>; the vendor is a passive party — she initiates
 * nothing and is paid by having coins deleted from the buyer's side (no coin ever moves to her).
 *
 * <p>Like {@code hit} and {@code talk}, a two-interaction designation converging on one outcome — the player
 * names the ware by a descriptive fragment or, when that is ambiguous, by choosing from a numbered menu:
 *
 * <ul>
 *   <li>{@link #playerBuysTarget(String)} — designate by a descriptive fragment (the disambiguation lives in
 *       the {@code select} subcase: no match, exactly one, or a menu offered).</li>
 *   <li>{@link #playerBuysChosenCandidate(int, List)} — designation by choosing from the last-offered
 *       candidates, completing the disambiguation.</li>
 * </ul>
 *
 * <p>Both interactions share one write tail (count the coins, refuse the shortfall, mint and pay): the
 * presentation on offer — ware bought, not enough coins, or a subcase outcome — is reached exactly once per
 * call, per the "presentation is terminal" discipline every use case honours.
 */
public interface BuyFromNpcInputPort {

    /**
     * Buys the ware the given fragment designates among the scene's standing offers.
     *
     * @param target the descriptive fragment the player typed (the select subcase matches it against the
     *               offer names; blank or unmatched targets are presented outcomes)
     */
    void playerBuysTarget(String target);

    /**
     * Completes a by-choice designation: buys the ware at the given ordinal of the last-offered menu, its
     * tokens re-confirmed against the live offers before anything is paid.
     *
     * @param ordinal          the 1-based number the player chose
     * @param offeredTokens    the candidate tokens of that offer, as the presenter armed them
     */
    void playerBuysChosenCandidate(int ordinal, List<String> offeredTokens);
}
