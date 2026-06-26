package com.github.gameclean.core.model.dice;

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
 */
@Value
public class Chance {

    int numerator;
    int denominator;

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
}
