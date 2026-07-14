package com.github.gameclean.core.model.npc;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.dice.Dice;
import com.github.gameclean.core.model.id.Ids;
import lombok.Value;

/**
 * Identity of an {@link Npc} — a Value Object wrapping an id of the form {@code npc} + a non-empty
 * body (e.g. {@code npcV1StGXR8}). The NPC twin of {@code ItemId}: same structure, different prefix.
 *
 * <p>Like {@code ItemId}, {@code SceneId} and {@code PlayerId}, the body is an opaque token: the model
 * validates only its <em>structure</em> — correct prefix, non-empty body, single token with no whitespace —
 * never its exact character set or length. Those are the <em>encoding</em> of a generated body, owned by
 * {@link Ids} (the model's single knower); validating them here would also wrongly reject the short authored
 * ids ({@code npc1}) the tests use. Equality is by value (the wrapped string).
 *
 * <p>Like {@code ItemId}, an {@code NpcId} is <em>generated at runtime</em> rather than authored, so it
 * carries two entry points. The plain constructor is for <em>reconstitution</em> (the persistence adapter
 * wraps a stored full id). {@link #mint(Dice)} is for <em>fresh generation</em>: the model rolls its own dice
 * for the body (via {@link Ids#randomBody(Dice)}) — no infrastructure id port — and this type owns the one
 * thing the domain owns about a generated id, the {@code npc} prefix and how it composes with the body, then
 * runs the same always-valid gate. Prefix here, encoding in {@code Ids}: one knower each, so the two cannot
 * drift.
 */
@Value
public class NpcId {

    /** Three-letter aggregate prefix for NPCs. */
    public static final String PREFIX = "npc";

    String value;

    public NpcId(String value) {
        String trimmed = DomainValidation.requireNonNull(value, "npc id must not be null").strip();
        if (trimmed.isEmpty()) {
            throw new InvalidDomainObjectError("npc id must not be blank");
        }
        if (!trimmed.startsWith(PREFIX)) {
            throw new InvalidDomainObjectError(
                    "npc id must start with prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.length() == PREFIX.length()) {
            throw new InvalidDomainObjectError(
                    "npc id must have a non-empty body after prefix '%s', got '%s'".formatted(PREFIX, trimmed));
        }
        if (trimmed.codePoints().anyMatch(Character::isWhitespace)) {
            throw new InvalidDomainObjectError(
                    "npc id must be a single token without whitespace, got '%s'".formatted(trimmed));
        }
        this.value = trimmed;
    }

    /**
     * Mints a fresh npc id by rolling the given {@link Dice} for the body. The model generates its own
     * identity — no infrastructure id port — using the dice it already holds; {@link Ids} owns the body
     * encoding (alphabet and length) and this type owns the {@link #PREFIX} and composition.
     *
     * @param dice the dice to roll the body with
     * @return a freshly minted, valid npc id of the form {@code npc} + rolled body
     */
    public static NpcId mint(Dice dice) {
        return fromGeneratedBody(Ids.randomBody(dice));
    }

    /**
     * Composes an npc id from an already-generated body. The {@link #PREFIX} and the composition are the
     * domain's, kept here next to the structural validation that enforces them. A null body is rejected
     * outright rather than silently producing an {@code "npcnull"} id.
     *
     * @param body the rolled token, without prefix
     * @return a valid npc id of the form {@code npc} + {@code body}
     */
    public static NpcId fromGeneratedBody(String body) {
        return new NpcId(PREFIX + DomainValidation.requireNonNull(body, "npc id body must not be null"));
    }
}
