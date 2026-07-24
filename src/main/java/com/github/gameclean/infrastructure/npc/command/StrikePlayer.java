package com.github.gameclean.infrastructure.npc.command;

import lombok.Value;

/**
 * Command payload: the hostile, co-located NPC identified by {@link #npcToken} strikes the player. The
 * {@code npcToken} is the flattened NPC id ({@code NpcId.asString()}); the receiving session hands it inward as a
 * primitive and {@code FightNpc.npcStrikesPlayer} reconstructs the {@code NpcId}. Lombok {@code @Value} (final,
 * so it satisfies the sealed {@link NpcCommand}'s permit), matching the codebase's carrier shape.
 */
@Value
public class StrikePlayer implements NpcCommand {

    String npcToken;
}
