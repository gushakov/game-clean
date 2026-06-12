package com.github.gameclean.infrastructure.world;

/**
 * Parsed shape of a single authored exit — a direction {@code name} and the {@code target} scene's
 * authored id, both as raw strings. Like {@link SceneEntry}, it carries no domain types: the
 * {@code target} is the id <em>body</em> the value object will wrap, not a
 * {@link com.github.gameclean.core.model.scene.SceneId}.
 */
public record ExitEntry(String name, String target) {
}
