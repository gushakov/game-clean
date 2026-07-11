package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code Drop}, co-located with its use case. It carries only
 * {@code drop}'s <em>own</em> outcome plus the inherited catch-all; the orient not-founds and the
 * disambiguation outcomes belong to the {@code orient} and {@code select} subcase ports respectively, which
 * the concrete terminal presenter implements alongside this one as three flat interfaces (composition, not a
 * presenter base class) — exactly as {@code examine} and {@code take} compose them.
 *
 * <p><b>One outcome — deliberately no {@code presentItemGotAway} twin.</b> {@code take} distinguishes a
 * write-side lock loss because a ground item is contested by any actor in the scene; a held item is
 * single-writer (only its holder drops it), so that race does not exist here and no lock-loss outcome is
 * minted for it. Should the unreachable happen, the propagating {@code OptimisticLockingError} rides to
 * {@code presentError} as the fault-like surprise it would be — the contested→handler vs
 * single-writer→propagate contrast (design-notes §5).
 *
 * <p>Domain objects pass straight through ({@link Item}) — no response DTOs.
 */
public interface DropPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: the item now lies on the ground of the player's current scene (reached by either designation). */
    void presentItemDropped(Item item);
}
