package com.github.gameclean.core.usecase.explore;

/**
 * Driving (input) port for the <b>Look</b> user goal: the player looks around to observe their
 * current surroundings. A driving adapter — the interactive console — invokes it to describe the
 * scene the player currently occupies.
 *
 * <p>One interaction, {@link #playerLooksAround()}, whose initiating actor is the <em>player</em>.
 * Its name is the Cockburn step it implements — subject (the player) and predicate (looks around) —
 * not a bare service verb. It takes <b>no parameter</b>: the acting player is <em>ambient</em>, pulled
 * inside the use case from {@code PlayerOperationsOutputPort} rather than threaded across the boundary
 * as data. The unqualified "around" is deliberate, reserving a later {@code playerLooksAt(target)} for
 * examining a specific thing.
 *
 * <p>It is {@code void}: every outcome (the scene description, a missing player, a dangling
 * current-scene reference, or an unexpected error) is reported through the
 * {@link LookPresenterOutputPort}, never returned.
 */
public interface LookInputPort {

    /**
     * Describes the acting player's current scene — the player "looks around". Takes no actor
     * argument: the acting player is resolved inside the use case from
     * {@code PlayerOperationsOutputPort}.
     */
    void playerLooksAround();
}
