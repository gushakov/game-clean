package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

/**
 * Presenter (driven) output port for {@code TakeFrom}, co-located with its use case. It carries only the use
 * case's <em>own</em> outcomes plus the inherited catch-all; the orient not-founds and the content
 * disambiguation outcomes belong to the {@code orient} and {@code select} subcase ports, which the concrete
 * terminal presenter implements alongside this one as three flat interfaces.
 *
 * <p>The outcomes deliberately repeat three of {@code Take}'s by name ({@code presentItemAnchored},
 * {@code presentItemGotAway}, a taken-success) rather than extending {@code TakePresenterOutputPort}: ports
 * are flat, and a port declares exactly what its owner presents. Sharing the destination is no reason to share
 * the port — the success here names the container the item came out of, which {@code Take} cannot.
 *
 * <p>Domain objects pass straight through ({@link Item}, {@link ItemId}) — no response DTOs.
 */
public interface TakeFromPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /**
     * Nothing on the ground here that can hold things is designated by the container fragment — no such
     * thing lies here, or what the fragment designates is not a container. Decided before any content is
     * looked at.
     */
    void presentNoSuchContainer(String containerTarget);

    /**
     * Happy path: the item has been taken out of the container into the player's keeping (reached by either
     * designation).
     *
     * @param item      the item as taken — now held by the player
     * @param container the container it came out of
     */
    void presentItemTakenFrom(Item item, Item container);

    /**
     * The take lost a concurrent race: the item was inside when selected but another actor's write committed
     * first, so this take's versioned write was rejected and rolled back — the write-side twin of
     * {@code select}'s read-side {@code presentTargetNoLongerAvailable}.
     */
    void presentItemGotAway(ItemId itemId);

    /**
     * The contained item is itself <em>anchored</em> — fixed where it is — so the take is refused before any
     * write. The gate reads the item being taken, never the container: an anchored container (a corpse, a
     * heavy chest) is exactly what one takes <em>from</em>.
     */
    void presentItemAnchored(Item item);
}
