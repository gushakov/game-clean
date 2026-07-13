package com.github.gameclean.core.usecase.inventory;

/**
 * Driving (input) port for the <b>Inventory</b> user goal: the player reviews everything they are
 * carrying. The summary goal's one <em>read</em> beside {@code take}/{@code drop}'s writes — a single
 * interaction, no target to designate and so no disambiguation follow-up, no transaction.
 *
 * <p>The acting player is ambient — resolved inside the use case, nothing crosses inward. Deliberately
 * <b>not</b> opened with the shared {@code orient} prologue: inventory is grounded in the <em>player
 * alone</em>, not in where they stand, so resolving the current scene would both over-fetch and mint a
 * false failure (a dangling current-scene reference must not block looking into one's own pockets).
 * Outcome-sharing tracks the shared prologue, and this goal does not share it.
 *
 * <p>{@code void} like every interaction: the outcome is reported through
 * {@link InventoryPresenterOutputPort}, never returned.
 */
public interface InventoryInputPort {

    /**
     * The player takes stock of the items in their keeping. Presents the carried items (possibly none —
     * an empty keeping is the same outcome, phrased by the renderer), or player-not-found when the
     * ambient player does not resolve.
     */
    void playerReviewsBelongings();
}
