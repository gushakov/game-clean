package com.github.gameclean.core.usecase.explore;

/**
 * Driving (input) port for the <b>Move</b> user goal: the player moves to an adjacent scene by going
 * through one of the current scene's named exits. A driving adapter — the interactive console — invokes
 * it with the exit the player chose.
 *
 * <p>One interaction, {@link #playerMovesThrough(String)}, whose initiating actor is the <em>player</em>.
 * Its name is the Cockburn step — subject (the player) and predicate (moves through [an exit]) — not a
 * bare service verb, mirroring {@code look}'s {@code playerLooksAround}. The acting player is
 * <em>ambient</em> (resolved inside the use case from {@code PlayerOperationsOutputPort}); only the exit
 * name crosses the boundary, as a primitive.
 *
 * <p>It is {@code void}: every outcome (the entered scene's description, an unknown player, a dangling
 * current scene, no such exit, a dangling exit target, or an unexpected error) is reported through the
 * {@link MovePresenterOutputPort}, never returned.
 */
public interface MoveInputPort {

    /**
     * Moves the acting player through the named exit of their current scene into its target scene, then
     * describes that scene. Takes no actor argument: the acting player is resolved inside the use case.
     *
     * @param exitName the name of the exit to take (as the player typed it; matched case-insensitively)
     */
    void playerMovesThrough(String exitName);
}
