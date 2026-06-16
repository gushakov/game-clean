package com.github.gameclean.core.usecase.initialize;

/**
 * Driving (input) port for the <b>InitializeGame</b> user goal: bring a fresh game into a playable
 * starting state. A driving adapter — the boot-time {@code GameSeeder}, later any caller — invokes it
 * to construct the authored world, place the single player, and spawn the authored items.
 *
 * <p>One interaction, {@link #systemInitializesGame(GameSeed)}, whose initiating actor is the
 * <em>system at startup</em>. Its name is the Cockburn step it implements — subject (the system) and
 * predicate (initializes the game) — not a bare lifecycle verb like {@code initialize}. It is
 * {@code void}: its single success — a playable game, the world present, the player placed and items
 * spawned — and each failure (an invalid authored input, an unresolved exit target, an unknown starting
 * scene, an item spawning into an unknown scene, an unexpected error) is reported through the
 * {@link InitializeGamePresenterOutputPort}, never returned. Because a {@code present*} call relinquishes
 * control, exactly one of those outcomes is reached per run, as the interaction's last act.
 *
 * <p>The world→player and world→items orders are <em>domain</em> preconditions — a player needs a scene
 * to stand in, items need scenes to spawn into — not lifecycle ones, so they live <em>inside</em> the use
 * case rather than being sequenced by a caller: world construction, player placement and item spawning are
 * the three phases of this single interaction, and the latter two run only once the world is usable.
 * Authored input crosses the boundary as primitives — bundled in the {@link GameSeed} carrier — and the
 * value objects are constructed inside the use case, the single validity gate.
 */
public interface InitializeGameInputPort {

    /**
     * Constructs the authored world, places the single player, and spawns the authored items, idempotently:
     * scenes are seeded only if the store is still empty, the player is created only if none exists yet, and
     * items are spawned only if none have been spawned yet (all three guards live inside the use case, so the
     * guarantee holds no matter which adapter fires the interaction, and a restart never re-rolls spawns).
     * The player's own id is resolved inside the use case from {@code PlayerOperationsOutputPort}, not passed
     * in. A world that fails to construct stops the interaction before any player is created or item spawned.
     *
     * @param seed the authored scenes, the starting scene id, and the items to spawn — primitive carriers,
     *             possibly invalid by design; the use case validates them, resolves their exit and spawn
     *             targets, and checks that the starting scene resolves to a persisted scene
     */
    void systemInitializesGame(GameSeed seed);
}
