package com.github.gameclean.core.model.scene;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * Identity of a {@link Scene} — a Value Object wrapping an authored id of the form
 * {@code scn} + a non-empty body (e.g. {@code scn1}).
 *
 * <p>The body is an opaque token: the model validates only its <em>structure</em> — correct
 * prefix, non-empty body, single token with no whitespace — never its character set. The
 * id-encoding alphabet is an artifact of the id-generation scheme, owned solely by the
 * generator adapter, so there is no shared pattern for the model to keep in sync. Equality
 * is by value (the wrapped string).
 */
@Getter
@EqualsAndHashCode
public class SceneId {

    /** Three-letter aggregate prefix for scenes. */
    public static final String PREFIX = "scn";

    private final String value;

    public SceneId(String value) {
        String trimmed = Objects.requireNonNull(value, "scene id must not be null").strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("scene id must not be blank");
        }
        if (!trimmed.startsWith(PREFIX)) {
            throw new IllegalArgumentException(
                    "scene id must start with prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.length() == PREFIX.length()) {
            throw new IllegalArgumentException(
                    "scene id must have a non-empty body after prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.codePoints().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "scene id must be a single token without whitespace, got '%s'".formatted(trimmed));
        }
        this.value = trimmed;
    }
}
