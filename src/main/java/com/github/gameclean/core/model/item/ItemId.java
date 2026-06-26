package com.github.gameclean.core.model.item;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.id.Ids;
import lombok.Value;

/**
 * Identity of an {@link Item} — a Value Object wrapping an id of the form {@code itm} + a non-empty
 * body (e.g. {@code itmV1StGXR8}).
 *
 * <p>Like {@code SceneId} and {@code PlayerId}, the body is an opaque token: the model validates only
 * its <em>structure</em> — correct prefix, non-empty body, single token with no whitespace — never its
 * exact character set or length. Those are the <em>encoding</em> of a generated body, owned by {@link Ids}
 * (the model's single knower); validating them here would also wrongly reject the short authored ids
 * ({@code itm1}) the seed and tests use. Equality is by value (the wrapped string).
 *
 * <p>{@code ItemId} is the first aggregate id <em>generated at runtime</em> rather than authored or
 * configured, so it carries two entry points. The plain constructor is for <em>reconstitution</em> (the
 * persistence adapter wraps a stored full id), exactly as {@code SceneId}/{@code PlayerId} are only ever
 * used. {@link #mint(Dice)} is for <em>fresh generation</em>: the model rolls its own dice for the body (via
 * {@link Ids#randomBody(Dice)}) — no infrastructure id port — and this type owns the one thing the domain
 * owns about a generated id, the {@code itm} prefix and how it composes with the body, then runs the same
 * always-valid gate. Prefix here, encoding in {@code Ids}: one knower each, so the two cannot drift.
 */
@Value
public class ItemId {

    /** Three-letter aggregate prefix for items. */
    public static final String PREFIX = "itm";

    String value;

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
     * Mints a fresh item id by rolling the given {@link Dice} for the body. The model generates its own
     * identity — no infrastructure id port — using the dice it already holds; {@link Ids} owns the body
     * encoding (alphabet and length) and this type owns the {@link #PREFIX} and composition.
     *
     * @param dice the dice to roll the body with
     * @return a freshly minted, valid item id of the form {@code itm} + rolled body
     */
    public static ItemId mint(Dice dice) {
        return fromGeneratedBody(Ids.randomBody(dice));
    }

    /**
     * Composes an item id from an already-generated body. The {@link #PREFIX} and the composition are the
     * domain's, kept here next to the structural validation that enforces them. A null body is rejected
     * outright rather than silently producing an {@code "itmnull"} id.
     *
     * @param body the rolled token, without prefix
     * @return a valid item id of the form {@code itm} + {@code body}
     */
    public static ItemId fromGeneratedBody(String body) {
        return new ItemId(PREFIX + DomainValidation.requireNonNull(body, "item id body must not be null"));
    }
}
