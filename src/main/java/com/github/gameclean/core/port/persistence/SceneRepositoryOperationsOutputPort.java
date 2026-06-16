package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;

import java.util.Optional;

/**
 * Driven (output) port for world persistence — the use case drives it; an infrastructure
 * adapter implements it. The {@code OutputPort} suffix marks the hexagonal direction: the
 * core is the caller, the infrastructure ring the implementor.
 *
 * <p>Scoped to what its use cases need: an emptiness check and per-scene save for
 * {@code InitializeGame}'s seed-if-empty guard, and a lookup by id for {@code Look} (the read that
 * retired the spike's direct repository access). It trades in the domain {@link Scene} model — the
 * adapter hides its own persistence shape.
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

    /**
     * @return the scene with the given id, or empty if none is persisted. The {@code Look} use case
     *         resolves the player's current scene through this lookup.
     * @throws PersistenceOperationsError if the lookup fails
     */
    Optional<Scene> findScene(SceneId id);
}
