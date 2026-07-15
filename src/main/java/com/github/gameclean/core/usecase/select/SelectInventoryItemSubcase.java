package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;

import java.util.List;

/**
 * Resolves which item in the player's <b>keeping</b> they mean — the inventory-sourced concrete of the
 * {@code select} Template Method, used by {@code drop}. Its arrival is the second provisioner that extracted
 * {@link AbstractSelectTargetSubcase} (issue #55). Framework-free, constructed by the composition root and
 * handed the <em>same presenter instance</em> as the parent it serves.
 *
 * <p>The whole dialogue skeleton lives on the base; this concrete binds the two type-specific facts — its
 * provenance (the items held by the given player, through the port it holds; the subcase owns its
 * provisioning, so a parent passes only the {@link PlayerId} coordinate) and its candidate type's token
 * shape (an {@link ItemId}).
 */
public class SelectInventoryItemSubcase extends AbstractSelectTargetSubcase<PlayerId, Item> {

    private final ItemRepositoryOperationsOutputPort itemOps;

    public SelectInventoryItemSubcase(SelectTargetPresenterOutputPort<Item> presenter,
                                      ItemRepositoryOperationsOutputPort itemOps) {
        super(presenter);
        this.itemOps = itemOps;
    }

    @Override
    protected List<Item> provisionCandidates(PlayerId holder) {
        return itemOps.findItemsHeldBy(holder);
    }

    @Override
    protected void requireWellFormedToken(String idToken) {
        // Reconstitution as pure validity gate — the instance is discarded; only the throw on a malformed
        // token matters.
        new ItemId(idToken);
    }
}
