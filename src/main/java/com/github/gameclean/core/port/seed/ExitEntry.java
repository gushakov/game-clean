package com.github.gameclean.core.port.seed;

import lombok.Value;

/**
 * Parsed shape of a single authored exit — a direction {@code name} and the {@code target} scene's
 * authored id, both as raw strings. Like {@link SceneEntry}, it carries no domain types: the
 * {@code target} is the id the value object will wrap, not a
 * {@link com.github.gameclean.core.model.scene.SceneId}. Part of the
 * {@link GameSeedSourceOperationsOutputPort} return contract.
 *
 * <p>Lombok {@code @Value} (not a Java record), matching the Lombok shape used across the codebase.
 */
@Value
public class ExitEntry {

    String name;
    String target;
}
