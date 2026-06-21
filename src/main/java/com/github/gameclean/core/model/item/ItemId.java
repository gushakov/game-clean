package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

/**
 * Identity of an {@link Item} — a Value Object wrapping an id of the form {@code itm} + a non-empty
 * body (e.g. {@code itmV1StGXR8}).
 *
 * <p>Like {@code SceneId} and {@code PlayerId}, the body is an opaque token: the model validates only
 * its <em>structure</em> — correct prefix, non-empty body, single token with no whitespace — never its
 * character set, which is an artifact of the id-generation scheme owned solely by the generator adapter.
 * Equality is by value (the wrapped string).
 *
 * <p>{@code ItemId} is the first aggregate id that is <em>generated at runtime</em> rather than authored
 * or configured, so it carries two entry points. The plain constructor is for <em>reconstitution</em>
 * (the persistence adapter wraps a stored full id), exactly as {@code SceneId}/{@code PlayerId} are only
 * ever used. {@link #fromGeneratedBody(String)} is for <em>fresh generation</em>: the id generator port
 * supplies a body of its own private alphabet, and this factory owns the one thing the domain owns about
 * a generated id — the prefix and how it composes with the body — then runs the same always-valid gate.
 */
@Value
public class ItemId {

    /** Three-letter aggregate prefix for items. */
    public static final String PREFIX = "itm";

    private final String value;

    public ItemId(String value) {
        String trimmed = DomainValidation.requireNonNull(value, "item id must not be null").strip();
        if (trimmed.isEmpty()) {
            throw new InvalidDomainObjectError("item id must not be blank");
        }
        if (!trimmed.startsWith(PREFIX)) {
            throw new InvalidDomainObjectError(
                    "item id must start with prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.length() == PREFIX.length()) {
            throw new InvalidDomainObjectError(
                    "item id must have a non-empty body after prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.codePoints().anyMatch(Character::isWhitespace)) {
            throw new InvalidDomainObjectError(
                    "item id must be a single token without whitespace, got '%s'".formatted(trimmed));
        }
        this.value = trimmed;
    }

    /**
     * Composes an item id from a freshly generated body. The body is the part the id generator owns
     * (its alphabet and length are the generator adapter's private concern); the {@link #PREFIX} and the
     * composition are the domain's, kept here next to the structural validation that enforces them. A null
     * body is rejected outright rather than silently producing an {@code "itmnull"} id.
     *
     * @param body the generator-supplied token, without prefix
     * @return a valid item id of the form {@code itm} + {@code body}
     */
    public static ItemId fromGeneratedBody(String body) {
        return new ItemId(PREFIX + DomainValidation.requireNonNull(body, "item id body must not be null"));
    }
}
