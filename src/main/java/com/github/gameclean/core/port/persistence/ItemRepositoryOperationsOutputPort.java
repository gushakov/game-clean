package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;

import java.util.List;

/**
 * Driven (output) port for item persistence — the use case drives it; an infrastructure adapter
 * implements it. The {@code OutputPort} suffix marks the hexagonal direction: the core is the caller,
 * the infrastructure ring the implementor.
 *
 * <p>Scoped to what its use cases need: the two location queries over the item's by-identity
 * {@code Location} reference — the items on the ground in a scene ({@code look}/{@code move} present them;
 * the scene-ground {@code select} provisions from them) and the items a player holds (the inventory
 * {@code select} provisions from them, for {@code drop}) — a per-item save (spawning inserts, {@code take}/
 * {@code drop} update), and an emptiness check for the spawn idempotency guard. Following the established
 * driven-port convention, it trades in the domain {@link Item} model — the adapter hides its own
 * persistence shape.
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
     * Persists a single item (insert). Called once per spawned instance during world initialization.
     *
     * @throws PersistenceOperationsError if the save fails
     */
    void saveItem(Item item);

    /**
     * @return {@code true} once at least one item has been spawned into the world. The item-spawning
     *         idempotency guard relies on this — inside the seed transaction — to decide whether spawning
     *         is still needed, so a restart never re-rolls placements.
     * @throws PersistenceOperationsError if the check fails
     */
    boolean itemsAlreadySpawned();
}
