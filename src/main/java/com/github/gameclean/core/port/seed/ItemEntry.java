package com.github.gameclean.core.port.seed;

import lombok.Value;

/**
 * Parsed shape of a single authored item — its descriptions and its {@link SpawnEntry spawn rule} — a
 * flat, immutable carrier of primitives holding no domain types. Part of the
 * {@link GameSeedSourceOperationsOutputPort} return contract; the
 * {@link com.github.gameclean.core.model.item.ItemTemplate} value object is constructed inside the use
 * case (the validity gate).
 *
 * <p>The {@code id} is the <em>authoring handle</em> used in the seed file and in diagnostics (e.g.
 * reporting that an item spawns into an unknown scene); it is deliberately <em>not</em> an item instance
 * id. One template spawns several instances, each minted a fresh generated {@code ItemId}, so the authored
 * handle never becomes a persisted instance id.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the shape used across the codebase.
 */
@Value
public class ItemEntry {

    String id;
    String shortDescription;
    String fullDescription;
    SpawnEntry spawn;
}
