package com.github.gameclean.core.usecase.initialize;

import java.util.List;

/**
 * Driving (input) port for the <b>InitializeGame</b> user goal: bring a fresh game into a playable
 * starting state. A driving adapter — the boot-time {@code GameSeeder}, later any caller — invokes it
 * to construct the authored world and place the single player in it.
 *
 * <p>One interaction, {@link #initialize(List, String)}, whose initiating actor is the <em>system at
 * startup</em>. It is {@code void}: every outcome (world seeded, world already present, an invalid
 * authored input, an unresolved exit target, a player created, a player that already exists, an unknown
 * starting scene, or an unexpected error) is reported through the
 * {@link InitializeGamePresenterOutputPort}, never returned.
 *
 * <p>The world→player order is a <em>domain</em> precondition — a player needs a scene to stand in —
 * not a lifecycle one, so it lives <em>inside</em> the use case rather than being sequenced by a
 * caller: world construction and player seeding are private steps of this single interaction, and the
 * player is placed only once the world is usable. Authored input crosses the boundary as primitives —
 * the {@link SceneEntry} carriers for the scenes, a bare {@code String} for the starting scene id — and
 * the value objects are constructed inside the use case, the single validity gate.
 */
public interface InitializeGameInputPort {

    /**
     * Constructs the authored world and places the single player in it, idempotently: scenes are seeded
     * only if the store is still empty, and the player is created only if none exists yet (both guards
     * live inside the use case, so the guarantee holds no matter which adapter fires the interaction).
     * The player's own id is resolved inside the use case from {@code PlayerOperationsOutputPort}, not
     * passed in. A world that fails to construct stops the interaction before any player is created.
     *
     * @param sceneEntries    the authored scenes, as primitive carriers (possibly invalid by design —
     *                        the use case validates them and resolves their exit targets)
     * @param startingSceneId the scene the player starts in, as a primitive carrier (possibly invalid
     *                        by design — the use case validates it and checks that it resolves to a
     *                        persisted scene)
     */
    void initialize(List<SceneEntry> sceneEntries, String startingSceneId);
}
