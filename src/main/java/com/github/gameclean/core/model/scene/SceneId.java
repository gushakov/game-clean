package com.github.gameclean.core.model.scene;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;

/**
 * Identity of a {@link Scene} — a Value Object wrapping an authored id of the form
 * {@code scn} + a non-empty body (e.g. {@code scn1}).
 *
 * <p>The body is an opaque token: the model validates only its <em>structure</em> — correct
 * prefix, non-empty body, single token with no whitespace — never its character set. The
 * id-encoding alphabet is an artifact of the id-generation scheme, owned solely by the
 * generator adapter, so there is no shared pattern for the model to keep in sync. Equality
 * is by value (the wrapped string).
 *
 * <p>The wrapped representation is not exposed as a structural getter. Callers that need a text form
 * ask for one explicitly via {@link #asString()} — a semantic projection, deliberately distinct from
 * {@link #toString()} (developer-facing / debug-only) — and construct via the {@link #of(String)} factory.
 * Binding to a capability rather than to "the field is a {@code String}" keeps call sites stable if the
 * wrapped type ever changes.
 */
@Value
public class SceneId {

    /** Three-letter aggregate prefix for scenes. */
    public static final String PREFIX = "scn";

    @Getter(AccessLevel.NONE)
    String value;

    /** Reconstitutes a scene id from its stored/authored text form, running the always-valid gate. */
    public static SceneId of(String value) {
        return new SceneId(value);
    }

    /** The canonical text form of this id — for logs, UI, keys, and persistence. */
    public String asString() {
        return value;
    }

    private SceneId(String value) {
        String trimmed = DomainValidation.requireNonNull(value, "scene id must not be null").strip();
        if (trimmed.isEmpty()) {
            throw new InvalidDomainObjectError("scene id must not be blank");
        }
        if (!trimmed.startsWith(PREFIX)) {
            throw new InvalidDomainObjectError(
                    "scene id must start with prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.length() == PREFIX.length()) {
            throw new InvalidDomainObjectError(
                    "scene id must have a non-empty body after prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.codePoints().anyMatch(Character::isWhitespace)) {
            throw new InvalidDomainObjectError(
                    "scene id must be a single token without whitespace, got '%s'".formatted(trimmed));
        }
        this.value = trimmed;
    }
}
