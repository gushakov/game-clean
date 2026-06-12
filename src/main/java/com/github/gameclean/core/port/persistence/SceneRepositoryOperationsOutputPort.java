package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.scene.Scene;

/**
 * Driven (output) port for world persistence — the use case drives it; an infrastructure
 * adapter implements it. The {@code OutputPort} suffix marks the hexagonal direction: the
 * core is the caller, the infrastructure ring the implementor.
 *
 * <p>Scoped to what {@code ConstructWorld} needs for the first cut: an emptiness check for the
 * seed-if-empty idempotency guard, and a per-scene save. Read/query methods are deliberately
 * absent until a use case (look / move) actually needs them.
 *
 * <p>Failures surface as the unchecked {@link PersistenceOperationsError}, caught at the use-case
 * checkpoint; methods declare no {@code throws} so they compose directly as transaction actions.
 */
public interface SceneRepositoryOperationsOutputPort {

    /**
     * @return {@code true} when no scene has been persisted yet. The world-construction
     *         idempotency guard relies on this to decide whether seeding is needed.
     * @throws PersistenceOperationsError if the emptiness check fails
     */
    boolean worldIsEmpty();

    /**
     * Persists a single scene (insert). Called once per scene during world construction.
     *
     * @throws PersistenceOperationsError if the save fails
     */
    void saveScene(Scene scene);
}
