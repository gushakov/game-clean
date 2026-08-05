package com.github.gameclean.infrastructure.npc.command;

import lombok.Value;

/**
 * Command payload: the NPC identified by {@link #npcToken} wanders through the exit named {@link #exitName}. The
 * exit was chosen by the policy (which owns the dice) and carried here, so the executing interaction never
 * re-rolls. The {@code npcToken} is the flattened NPC id ({@code NpcId.asString()}); the receiving session hands
 * both inward as primitives and {@code Wander.npcWandersThrough} reconstructs the {@code NpcId}. Lombok
 * {@code @Value} (final, so it satisfies the sealed {@link NpcCommand}'s permit).
 */
@Value
public class WanderThrough implements NpcCommand {

    String npcToken;
    String exitName;
}
