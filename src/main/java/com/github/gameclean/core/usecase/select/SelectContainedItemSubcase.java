package com.github.gameclean.core.usecase.select;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;

import java.util.List;

/**
 * Resolves which item <b>inside a set of containers</b> the player means — the container-sourced concrete of
 * the {@code select} Template Method, used by {@code take … from …}. Framework-free, constructed by the
 * composition root and handed the <em>same presenter instance</em> as the parent it serves.
 *
 * <p>The coordinate is the <em>set of containers to look in</em>, as their ids: the candidates are the union
 * of those containers' contents. The parent decides which containers are in play (the ones a fragment
 * designates on the first interaction, every container on the ground here when re-validating a menu pick), so
 * this concrete never learns what makes a container reachable — it only reads what lies inside the ones it is
 * given. A container owns no collection, so "the contents of C" is the port's containment query, one read per
 * container.
 *
 * <p>The whole dialogue skeleton lives on {@link AbstractSelectTargetSubcase}; this concrete binds the two
 * type-specific facts — its provenance and its candidate type's token shape (an {@link ItemId}).
 */
public class SelectContainedItemSubcase extends AbstractSelectTargetSubcase<List<ItemId>, Item> {

    private final ItemRepositoryOperationsOutputPort itemOps;

    public SelectContainedItemSubcase(SelectTargetPresenterOutputPort<Item> presenter,
                                      ItemRepositoryOperationsOutputPort itemOps) {
        super(presenter);
        this.itemOps = itemOps;
    }

    @Override
    protected List<Item> provisionCandidates(List<ItemId> containers) {
        return containers.stream()
                .flatMap(container -> itemOps.findItemsInside(container).stream())
                .toList();
    }

    @Override
    protected void requireWellFormedToken(String idToken) {
        // Reconstitution as pure validity gate — the instance is discarded; only the throw on a malformed
        // token matters.
        ItemId.of(idToken);
    }
}
