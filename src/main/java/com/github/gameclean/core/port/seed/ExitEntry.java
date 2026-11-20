package com.github.gameclean.core.port.seed;

import lombok.Value;

/**
 * Parsed shape of a single authored exit — a direction {@code name} and the {@code target} scene's
 * authored id, both as raw strings, plus an optional {@code lock} handle and the optional
 * {@code lockedDescription} shown when a move through the locked exit is refused (both {@code null} when not
 * authored; a description without a lock is a gate rule, not a parse-time concern). Like {@link SceneEntry}, it carries no domain types: the
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
    String lock;
    String lockedDescription;
}
