package com.github.gameclean.core.port.seed;

import lombok.Value;

/**
 * Parsed shape of a single authored NPC — its descriptions, its {@link SpawnEntry spawn rule}, and its
 * move chance — a flat, immutable carrier of primitives holding no domain types. The NPC twin of
 * {@link ItemEntry}, part of the {@link GameSeedSourceOperationsOutputPort} return contract; the
 * {@link com.github.gameclean.core.model.npc.NpcTemplate} value object is constructed inside the use case
 * (the validity gate).
 *
 * <p>The {@code id} is the <em>authoring handle</em> used in the seed file and in diagnostics (e.g. reporting
 * that an NPC spawns into an unknown scene); it is deliberately <em>not</em> an NPC instance id. One template
 * spawns several instances, each minted a fresh generated {@code NpcId}, so the authored handle never becomes
 * a persisted instance id.
 *
 * <p>The move chance is carried as a numerator/denominator pair (parsed from a {@code "1/4"} fraction), like
 * {@link SpawnEntry}'s chance — the {@link com.github.gameclean.core.model.dice.Chance} value object is
 * constructed inside the use case.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the shape used across the codebase.
 */
@Value
public class NpcEntry {

    String id;
    String shortDescription;
    String fullDescription;
    SpawnEntry spawn;
    int moveChanceNumerator;
    int moveChanceDenominator;
}
