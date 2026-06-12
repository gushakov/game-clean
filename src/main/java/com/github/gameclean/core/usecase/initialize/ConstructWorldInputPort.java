package com.github.gameclean.core.usecase.initialize;

import java.util.List;

/**
 * Driving (input) port for the <b>ConstructWorld</b> user goal. A driving adapter — at first a
 * boot-time seeder, later any caller — invokes it to bring the authored world into existence.
 *
 * <p>One interaction, {@link #constructWorld(List)}, whose initiating actor is the <em>system at
 * startup</em>. It is {@code void}: every outcome (success, an already-constructed world, a
 * validation failure, an unresolved exit target, or an unexpected error) is reported through the
 * {@link ConstructWorldPresenterOutputPort}, never returned. Authored input crosses the boundary as
 * the primitive {@link SceneEntry} carriers; the value objects and aggregates are constructed inside
 * the use case, which is the single validity gate.
 */
public interface ConstructWorldInputPort {

    /**
     * Constructs the world from the authored scene entries and seeds it once, if and only if the
     * store is still empty (the idempotency guard lives inside the use case, so the guarantee holds
     * no matter which adapter fires the interaction).
     *
     * @param sceneEntries the authored scenes, as primitive carriers (possibly invalid by design —
     *                     the use case validates them)
     */
    void constructWorld(List<SceneEntry> sceneEntries);
}
