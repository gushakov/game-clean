package com.github.gameclean.core.model.player;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Value;

/**
 * Identity of a {@link Player} — a Value Object wrapping an authored id of the form
 * {@code plr} + a non-empty body (e.g. {@code plr1}).
 *
 * <p>Like {@code SceneId}, the body is an opaque token: the model validates only its
 * <em>structure</em> — correct prefix, non-empty body, single token with no whitespace — never its
 * character set, which is an artifact of the id-generation scheme owned solely by the generator
 * adapter. Equality is by value (the wrapped string).
 *
 * <p>The wrapped representation is not exposed as a structural getter. Callers that need a text form
 * ask for one explicitly via {@link #asString()} — a semantic projection, deliberately distinct from
 * {@link #toString()} — and construct via the {@link #of(String)} factory.
 */
@Value
public class PlayerId {

    /** Three-letter aggregate prefix for players. */
    public static final String PREFIX = "plr";

    @Getter(AccessLevel.NONE)
    String value;

    /** Reconstitutes a player id from its stored/authored text form, running the always-valid gate. */
    public static PlayerId of(String value) {
        return new PlayerId(value);
    }

    /** The canonical text form of this id — for logs, UI, keys, and persistence. */
    public String asString() {
        return value;
    }

    private PlayerId(String value) {
        String trimmed = DomainValidation.requireNonNull(value, "player id must not be null").strip();
        if (trimmed.isEmpty()) {
            throw new InvalidDomainObjectError("player id must not be blank");
        }
        if (!trimmed.startsWith(PREFIX)) {
            throw new InvalidDomainObjectError(
                    "player id must start with prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.length() == PREFIX.length()) {
            throw new InvalidDomainObjectError(
                    "player id must have a non-empty body after prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.codePoints().anyMatch(Character::isWhitespace)) {
            throw new InvalidDomainObjectError(
                    "player id must be a single token without whitespace, got '%s'".formatted(trimmed));
        }
        this.value = trimmed;
    }
}
