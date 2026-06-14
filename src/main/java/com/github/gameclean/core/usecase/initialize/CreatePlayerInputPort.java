package com.github.gameclean.core.usecase.initialize;

/**
 * Driving (input) port for the <b>CreatePlayer</b> user goal: the single player is brought into
 * existence at a starting scene. A driving adapter — at first a boot-time seeder, later any caller —
 * invokes it to create the player, once, before that player can act.
 *
 * <p>One interaction, {@link #createPlayer(String)}, whose initiating actor is the <em>system at
 * startup</em> (the same actor as {@link ConstructWorldInputPort}). It is {@code void}: every outcome
 * (success, a player that already exists, a validation failure, an unknown starting scene, or an
 * unexpected error) is reported through the {@link CreatePlayerPresenterOutputPort}, never returned.
 *
 * <p>The starting scene crosses the boundary as a <b>primitive</b> {@code String} carrier — authored
 * configuration, invalid-capable by design, just like the {@link SceneEntry} carriers
 * {@code ConstructWorld} accepts — and its {@code SceneId} value object is constructed inside the use
 * case, the single validity gate. The player's own id is <em>not</em> a parameter: like {@code Look}'s
 * acting player it is ambient, pulled inside the use case from {@code PlayerOperationsOutputPort}.
 */
public interface CreatePlayerInputPort {

    /**
     * Creates the single player at the given starting scene and persists it once, if and only if no
     * player exists yet (the idempotency guard lives inside the use case, so the guarantee holds no
     * matter which adapter fires the interaction). The acting player's id is resolved inside the use
     * case from {@code PlayerOperationsOutputPort}, not passed in.
     *
     * @param startingSceneId the scene the player starts in, as a primitive carrier (possibly invalid
     *                        by design — the use case validates it and checks that it resolves to a
     *                        persisted scene)
     */
    void createPlayer(String startingSceneId);
}
