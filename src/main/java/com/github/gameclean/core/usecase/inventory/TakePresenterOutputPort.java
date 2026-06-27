package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code Take}, co-located with its use case. It carries only {@code take}'s
 * <em>own</em> outcomes plus the inherited catch-all; the orient not-founds and the disambiguation outcomes
 * belong to the {@code orient} and {@code select} subcase ports respectively, which the concrete terminal
 * presenter implements alongside this one as three flat interfaces (composition, not a presenter base class) —
 * exactly as {@code examine} composes them.
 *
 * <p><b>Two outcomes, and why {@code presentItemGotAway} is distinct from {@code select}'s
 * {@code presentItemNoLongerHere}.</b> They are genuinely different domain outcomes that happen to read the
 * same to the player: {@code presentItemNoLongerHere} is the <em>read-side</em> miss (re-provisioning for a
 * menu pick found the item already gone), while {@code presentItemGotAway} is the <em>write-side</em> loss (we
 * read the item as present but lost the optimistic-locking race at commit). The single-match
 * {@code take rusty} path does no read-side re-check, so the write-side guard is its only concurrency net —
 * the two are reachable on different paths and so are kept separate, even though a renderer collapses them to
 * one line.
 *
 * <p>Domain objects pass straight through ({@link Item}, {@link ItemId}) — no response DTOs.
 */
public interface TakePresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: the item has been taken into the player's keeping (reached by either designation). */
    void presentItemTaken(Item item);

    /**
     * The take lost a concurrent race: the item was present when selected but another actor's write committed
     * first, so this take's versioned write was rejected and rolled back. The honest "someone got there first"
     * outcome — the write-side twin of {@code select}'s read-side {@code presentItemNoLongerHere}.
     */
    void presentItemGotAway(ItemId itemId);
}
