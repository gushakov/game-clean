package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;

import java.util.List;

/**
 * Driven (output) port for item persistence — the use case drives it; an infrastructure adapter
 * implements it. The {@code OutputPort} suffix marks the hexagonal direction: the core is the caller,
 * the infrastructure ring the implementor.
 *
 * <p>Scoped to what its use cases need: the three location queries over the item's by-identity
 * {@code Location} reference — the items on the ground in a scene ({@code look}/{@code move} present them;
 * the scene-ground {@code select} provisions from them), the items a player holds (the inventory
 * {@code select} provisions from them, for {@code drop}), and the items inside a container ({@code examine}
 * reveals them) — a per-item save (spawning inserts, {@code take}/{@code drop} update), and an emptiness
 * check for the spawn idempotency guard. Following the established driven-port convention, it trades in the
 * domain {@link Item} model — the adapter hides its own persistence shape.
 *
 * <p>Failures surface as the unchecked {@link PersistenceOperationsError}, caught at the use-case
 * checkpoint; methods declare no {@code throws} so they compose directly as transaction actions.
 */
public interface ItemRepositoryOperationsOutputPort {

    /**
     * @return the items currently located in the given scene (on the ground), empty if none.
     * @throws PersistenceOperationsError if the lookup fails
     */
    List<Item> findItemsInScene(SceneId sceneId);

    /**
     * @return the items currently held by the given player (their keeping), empty if none.
     * @throws PersistenceOperationsError if the lookup fails
     */
    List<Item> findItemsHeldBy(PlayerId holder);

    /**
     * @return the items currently inside the given container item, empty if none — the containment query
     *         against the contained items' {@code Location.Inside} references ({@code examine} reveals
     *         them; a future take-from-container will select from them).
     * @throws PersistenceOperationsError if the lookup fails
     */
    List<Item> findItemsInside(ItemId container);

    /**
     * Persists a single item (insert). Called once per spawned instance during world initialization.
     *
     * @throws PersistenceOperationsError if the save fails
     */
    void saveItem(Item item);

    /**
     * Deletes a single item, checked against the optimistic-locking version the given aggregate carries — the
     * write a purchase performs on each coin it pays: the coin rows are removed in the same transaction that
     * saves the delivered ware. Version-checked like {@link #saveItem(Item)}, and for the same reason the
     * {@code take} update is: a held item can have concurrent writers, and a stale delete must not remove a
     * row another writer has since moved past.
     *
     * @throws OptimisticLockingError     if the item was modified concurrently (a stale version)
     * @throws PersistenceOperationsError if the delete fails otherwise
     */
    void deleteItem(Item item);

    /**
     * @return {@code true} once at least one item has been spawned into the world. The item-spawning
     *         idempotency guard relies on this — inside the seed transaction — to decide whether spawning
     *         is still needed, so a restart never re-rolls placements.
     * @throws PersistenceOperationsError if the check fails
     */
    boolean itemsAlreadySpawned();
}
