package com.github.gameclean.core.usecase.npc;

import com.github.gameclean.core.model.npc.Npc;
import lombok.Value;

/**
 * One NPC movement the player can witness from their current scene — the unit of the {@code Wander} success
 * outcome (it moved here from the animate use case when combat split animate into a read-only policy and a
 * {@code Wander} executing interaction, #66 step 2). The use case classifies a move that touches the player's
 * scene into a {@link MovementKind} and pairs it with the NPC that moved and a {@code detail} the renderer
 * needs to phrase it:
 *
 * <ul>
 *   <li>{@link MovementKind#DEPARTED} — the NPC left the player's scene; {@code detail} is the <em>exit name</em>
 *       it left by (e.g. {@code "north"}).</li>
 *   <li>{@link MovementKind#ARRIVED} — the NPC entered the player's scene; {@code detail} is the <em>name of the
 *       scene</em> it came from (e.g. {@code "Courtyard"}).</li>
 * </ul>
 *
 * <p>The {@link Npc} is passed straight through (immutable, so the presenter cannot corrupt it — no Response-Model
 * DTO); the renderer reads its short description to name the wanderer. Lombok {@code @Value}, matching the shape
 * used across the codebase.
 */
@Value
public class PerceivedNpcMovement {

    Npc npc;
    MovementKind kind;
    String detail;
}
