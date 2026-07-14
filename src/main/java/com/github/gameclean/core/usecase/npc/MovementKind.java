package com.github.gameclean.core.usecase.npc;

/**
 * Which way a witnessed NPC movement crosses the player's current scene — the two perceptible kinds the animate
 * use case classifies a move into (moves touching neither the scene the player is in are not perceptible and
 * are never carried as a {@link PerceivedNpcMovement}).
 */
public enum MovementKind {

    /** The NPC left the player's current scene through an exit. */
    DEPARTED,

    /** The NPC arrived into the player's current scene from an adjacent one. */
    ARRIVED
}
