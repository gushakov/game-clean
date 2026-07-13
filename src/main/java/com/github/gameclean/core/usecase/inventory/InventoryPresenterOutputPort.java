package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.port.ErrorHandlingPresenterOutputPort;

import java.util.List;

/**
 * Presenter (driven) output port for {@code Inventory}, co-located with its use case.
 *
 * <p><b>One success outcome, empty list included.</b> "You are carrying nothing" is not a distinct
 * Cockburn stripe — the player's goal (know what I carry) is met identically by an empty answer, and
 * choosing the wording for it is formatting, the renderer's job. This mirrors {@code presentScene}'s
 * empty-ground precedent.
 *
 * <p><b>Own {@code presentPlayerNotFound}, not the orient port's.</b> The use case resolves the ambient
 * player inline (it deliberately skips the {@code orient} prologue — see {@link InventoryInputPort}),
 * so the not-found outcome belongs to this port; only the <em>rendering</em> is shared, via the same
 * {@code OrientRenderer} collaborator the orient-opened presenters compose (composition, not port reuse).
 *
 * <p>Domain objects pass straight through ({@link Item}) — no response DTOs.
 */
public interface InventoryPresenterOutputPort extends ErrorHandlingPresenterOutputPort {

    /** Happy path: the items in the player's keeping, possibly none (the renderer phrases the empty case). */
    void presentCarriedItems(List<Item> items);

    /** The ambient player does not resolve to a stored player — a configuration or data fault, surfaced plainly. */
    void presentPlayerNotFound(PlayerId playerId);
}
