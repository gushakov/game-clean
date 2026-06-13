package com.github.gameclean.core.usecase.explore;

/**
 * Driving (input) port for the <b>Look</b> user goal: the player observes their surroundings. A
 * driving adapter — the interactive console — invokes it to describe the scene the player currently
 * occupies.
 *
 * <p>One interaction, {@link #look(String)}, whose initiating actor is the <em>player</em>. It is
 * {@code void}: every outcome (the scene description, a missing player, a dangling current-scene
 * reference, or an unexpected error) is reported through the {@link LookPresenterOutputPort}, never
 * returned.
 *
 * <p>The player is identified by a primitive id carried across the boundary; the {@code PlayerId}
 * value object is constructed inside the use case, which is the validity gate. The id is a parameter
 * rather than ambient state because "which player" is the controller's to supply — single-player
 * today (one configured id), but the contract already admits more.
 */
public interface LookInputPort {

    /**
     * Describes the current scene of the identified player.
     *
     * @param playerId the acting player's id, as a primitive carrier (the use case validates it)
     */
    void look(String playerId);
}
