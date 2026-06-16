package com.github.gameclean.core.model.scene;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * A way out of a {@link Scene} — a Value Object owned by its source scene.
 *
 * <p>An exit carries a {@code name} (the direction label, e.g. {@code "east"}, unique within
 * its scene) and a {@code target}: a reference <em>by identity</em> to the scene this exit
 * leads to. The exit holds a {@link SceneId}, not a {@code Scene} — aggregates reference one
 * another by id. Whether the target scene actually exists is a world-consistency rule checked
 * by the world-construction use case, not here.
 *
 * <p>Always-valid: neither the name nor the target may be null, and the name may not be blank.
 * Equality is by value.
 */
@Getter
@EqualsAndHashCode
public class Exit {

    private final String name;
    private final SceneId target;

    public Exit(String name, SceneId target) {
        String trimmed = Objects.requireNonNull(name, "exit name must not be null").strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("exit name must not be blank");
        }
        this.name = trimmed;
        this.target = Objects.requireNonNull(target, "exit target must not be null");
    }
}
