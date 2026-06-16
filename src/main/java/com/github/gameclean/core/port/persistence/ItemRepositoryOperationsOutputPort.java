package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.scene.SceneId;

import java.util.List;

/**
 * Driven (output) port for item persistence — the use case drives it; an infrastructure adapter
 * implements it. The {@code OutputPort} suffix marks the hexagonal direction: the core is the caller,
 * the infrastructure ring the implementor.
 *
 * <p>Scoped to what its use cases need: a query of the items located in a scene (the {@code orient}
 * subcase reads "what is on the ground here" for {@code look}/{@code move} to present, and {@code pick}
 * will reuse it to find a named target), a per-item save (the boot initializer persists each spawned
 * instance), and an emptiness check for the spawn idempotency guard. Following the established
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
