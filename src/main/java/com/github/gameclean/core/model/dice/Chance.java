package com.github.gameclean.core.model.dice;

import com.github.gameclean.core.model.DomainValidation;
import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

/**
 * A spawn probability expressed as an authored fraction — e.g. {@code 12/50} — and the rule that decides
 * whether a single random draw hits it. A Value Object: always-valid (the denominator is positive and the
 * numerator lies in {@code [0, denominator]}, so the probability is in {@code [0, 1]}), equality by value.
 *
 * <p>{@code Chance} owns <em>what the probability is</em>; it does <em>not</em> own the randomness. It lives
 * beside {@link Dice} (the game's own source of chance) and is the odds {@link Dice#roll(Chance)} rolls
 * against: the dice draw, and this VO interprets the draw. Keeping the odds and the entropy as separate model
 * collaborators is why a {@link SeededDice} makes spawning deterministic under test while {@code Chance} stays
 * a pure, independently-tested rule.
 *
 * <p>The fraction has a canonical text form, {@code num/den} — the same rendering the authored world files
 * use — produced by {@link #asString()} and parsed back by {@link #of(String)}, which re-runs the always-valid
 * gate. Like the id value objects' {@code asString()}/{@code of()} pair, this is a semantic projection
 * (deliberately distinct from {@link #toString()}, the developer-facing debug form), so persistence can store
 * a chance as one readable scalar without coupling to its fields.
 */
@Value
public class Chance {

    int numerator;
    int denominator;

    /** Reconstitutes a chance from its canonical {@code num/den} text form, running the always-valid gate. */
    public static Chance of(String value) {
        String trimmed = DomainValidation.requireNonNull(value, "chance text must not be null").strip();
        String[] parts = trimmed.split("/", -1);
        if (parts.length != 2) {
            throw new InvalidDomainObjectError("chance text must have the form num/den, got '%s'".formatted(trimmed));
        }
        try {
            return new Chance(Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()));
        } catch (NumberFormatException e) {
            throw new InvalidDomainObjectError("chance text must have the form num/den, got '%s'".formatted(trimmed));
        }
    }

    public Chance(int numerator, int denominator) {
        if (denominator <= 0) {
            throw new InvalidDomainObjectError("chance denominator must be positive, got " + denominator);
        }
        if (numerator < 0) {
            throw new InvalidDomainObjectError("chance numerator must not be negative, got " + numerator);
        }
        if (numerator > denominator) {
            throw new InvalidDomainObjectError(
                    "chance numerator %d must not exceed denominator %d".formatted(numerator, denominator));
        }
        this.numerator = numerator;
        this.denominator = denominator;
    }

    /**
     * Whether a random draw hits this chance. The draw is expected in {@code [0, 1)} (the contract of a
     * {@link Dice} draw); a hit is {@code draw < numerator/denominator}. A zero numerator never hits; a
     * numerator equal to the denominator always hits.
     *
     * @param draw a uniform random draw in {@code [0, 1)}
     * @return {@code true} if the draw falls within this probability
     */
    public boolean isHitBy(double draw) {
        return draw < (double) numerator / denominator;
    }

    /** The canonical text form of these odds — e.g. {@code 12/50} — for persistence, authoring, and display. */
    public String asString() {
        return numerator + "/" + denominator;
    }
}
