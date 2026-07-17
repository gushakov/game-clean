package com.github.gameclean.core.model.combat;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import lombok.Value;

/**
 * A combatant's health pool — {@code current} out of {@code max} — and the rules for taking damage. A Value
 * Object: always-valid ({@code max} is strictly positive and {@code current} lies in {@code [0, max]}),
 * equality by value.
 *
 * <p>Lives in a neutral {@code combat/} concept package rather than under any one aggregate, following the
 * {@code dice/}, {@code designation/}, {@code spawn/} precedent: hit points are a combat concept, first cashed
 * by the {@code Npc} aggregate (the {@code hit} vertical, #66) and shared with {@code Player} once retaliation
 * demands player health (#66 step 2).
 *
 * <p>{@code HitPoints} owns <em>the health arithmetic</em> — damage never drives {@code current} below zero
 * (the clamp is a domain rule, not a use-case concern) — and answers {@link #isDepleted()}, the raw predicate
 * behind an NPC being dead. It is immutable: {@link #damage(int)} yields a new instance.
 */
@Value
public class HitPoints {

    int current;
    int max;

    public HitPoints(int current, int max) {
        if (max <= 0) {
            throw new InvalidDomainObjectError("hit points max must be strictly positive, got " + max);
        }
        if (current < 0) {
            throw new InvalidDomainObjectError("hit points current must not be negative, got " + current);
        }
        if (current > max) {
            throw new InvalidDomainObjectError(
                    "hit points current %d must not exceed max %d".formatted(current, max));
        }
        this.current = current;
        this.max = max;
    }

    /**
     * A full health pool of {@code max} hit points ({@code current == max}) — how a combatant spawns.
     *
     * @param max the maximum (and initial) hit points — must be strictly positive
     * @return full hit points
     */
    public static HitPoints full(int max) {
        return new HitPoints(max, max);
    }

    /**
     * Applies {@code amount} points of damage, returning a new {@code HitPoints} whose {@code current} is
     * lowered by that much but never below zero (the clamp is the domain rule; overkill is not negative
     * health). {@code max} is unchanged. A negative {@code amount} is a <em>caller programming error</em>, not
     * invalid domain input — the combat use case only ever deals a non-negative die roll — so it stays a plain
     * {@link IllegalArgumentException} (the behaviour-method guard convention), distinct from the construction
     * gate.
     *
     * @param amount the damage to apply (must not be negative)
     * @return a new {@code HitPoints} with {@code current} reduced by {@code amount}, floored at zero
     */
    public HitPoints damage(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("damage amount must not be negative, got " + amount);
        }
        return new HitPoints(Math.max(0, current - amount), max);
    }

    /**
     * Whether this pool is spent — {@code current == 0}. The raw predicate behind {@code Npc.isDead()}.
     *
     * @return {@code true} when no hit points remain
     */
    public boolean isDepleted() {
        return current == 0;
    }
}
