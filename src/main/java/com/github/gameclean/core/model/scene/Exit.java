package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Builder;
import lombok.Value;

/**
 * A way out of a {@link Scene} — a Value Object owned by its source scene.
 *
 * <p>An exit carries a {@code name} (the direction label, e.g. {@code "east"}, unique within
 * its scene) and a {@code target}: a reference <em>by identity</em> to the scene this exit
 * leads to. The exit holds a {@link SceneId}, not a {@code Scene} — aggregates reference one
 * another by id. Whether the target scene actually exists is a world-consistency rule checked
 * by the world-construction use case, not here.
 *
 * <p>An exit may carry a {@link Lock} ({@code null} for an ordinary exit). The lock is not a state of the
 * exit: an item that {@code opens} it lets its carrier through, and nobody else — whether a given mover may pass
 * is decided against what that mover carries, by the use case that moves it.
 *
 * <p>Always-valid: neither the name nor the target may be null, and the name may not be blank.
 * Equality is by value.
 */
@Value
public class Exit {

    String name;
    SceneId target;

    /** The lock barring this exit, or {@code null} for an ordinary exit anyone may pass. */
    Lock lock;

    /** An ordinary exit, with no lock. */
    public Exit(String name, SceneId target) {
        this(name, target, null);
    }

    @Builder
    public Exit(String name, SceneId target, Lock lock) {
        String trimmed = DomainValidation.requireNonNull(name, "exit name must not be null").strip();
        if (trimmed.isEmpty()) {
            throw new InvalidDomainObjectError("exit name must not be blank");
        }
        this.name = trimmed;
        this.target = DomainValidation.requireNonNull(target, "exit target must not be null");
        this.lock = lock;
    }

    /** Whether this exit carries a lock — passable only by a mover carrying an item that opens it. */
    public boolean hasLock() {
        return lock != null;
    }
}
