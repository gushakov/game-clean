package com.github.gameclean.core.usecase.initialize;

import lombok.Value;

import java.util.List;

/**
 * The complete input to {@code InitializeGame}: the authored scenes, the starting scene id, and the
 * authored items to spawn — bundled into one carrier so the input-port method takes a single argument
 * rather than a growing positional list. All primitives / {@code *Entry} carriers, no domain types: the
 * value objects are constructed inside the use case (the single validity gate).
 *
 * <p>The scenes and items come from the seed file; the starting scene id comes from configuration. The
 * driving adapter (the seed reader) assembles the three into this carrier before firing the interaction.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the shape used across the codebase.
 */
@Value
public class GameSeed {

    List<SceneEntry> scenes;
    String startingSceneId;
    List<ItemEntry> items;
}
