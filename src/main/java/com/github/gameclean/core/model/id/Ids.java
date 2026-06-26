package com.github.gameclean.core.model.id;

import com.github.gameclean.core.model.dice.Dice;

import java.util.List;
import java.util.Objects;

/**
 * Mints fresh aggregate-id bodies by rolling the game's own {@link Dice} — the in-hexagon successor of the
 * deleted {@code IdGeneratorOperationsOutputPort}. It is the model's <em>single knower</em> of the body's
 * alphabet and length, the role the NanoID adapter used to play: a generated body is {@link #BODY_LENGTH}
 * characters drawn uniformly from {@link #ALPHABET}. Because dice are a domain capability (not a port),
 * minting an id reaches nowhere outside the hexagon — the same algorithm NanoID ran (uniform draws over an
 * alphabet), now owned by the domain.
 *
 * <p>A stateless helper of pure static methods, fed a {@link Dice} as a value — the generation counterpart of
 * {@link com.github.gameclean.core.model.DomainValidation}. It mints only the <em>body</em>; the prefix and
 * composition stay on each id value object (e.g. {@code ItemId.mint}), so the prefix (a per-type domain
 * concern) and the body encoding (one knower, here) cannot drift — the split that {@code SceneId}/{@code
 * PlayerId}'s javadocs describe, now wholly inside the model.
 */
public final class Ids {

    /** The id-body alphabet: digits then upper- then lower-case letters ({@code [0-9A-Za-z]}, 62 glyphs). */
    public static final List<Character> ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"
                    .chars().mapToObj(c -> (char) c).toList();

    /**
     * The number of glyphs in a generated body. Eight base-62 glyphs give 62^8 (~2.18×10^14) distinct
     * bodies — ample collision headroom for this game's scale, where ids are minted only for spawned
     * instances. This is a deliberate domain choice now that the model owns id generation, no longer a
     * generator adapter's private default.
     */
    public static final int BODY_LENGTH = 8;

    private Ids() {
    }

    /**
     * Mints a fresh id body — {@link #BODY_LENGTH} glyphs, each picked uniformly from {@link #ALPHABET} by the
     * given {@link Dice}. The body carries no prefix; the calling id value object prepends its own. Uniqueness
     * is probabilistic (as NanoID's always was): distinct enough at this game's scale, not a hard guarantee.
     *
     * @param dice the dice to roll the glyphs with
     * @return a freshly rolled id body of length {@link #BODY_LENGTH}
     */
    public static String randomBody(Dice dice) {
        Objects.requireNonNull(dice, "dice must not be null");
        StringBuilder body = new StringBuilder(BODY_LENGTH);
        for (int i = 0; i < BODY_LENGTH; i++) {
            body.append(dice.pick(ALPHABET));
        }
        return body.toString();
    }
}
