package com.github.gameclean.core.model.dice;

import java.util.List;
import java.util.Objects;

/**
 * Skeleton {@link Dice} that expresses both domain operations in terms of a single source of uniform draws in
 * {@code [0, 1)}, leaving concrete implementations to supply only that source via {@link #nextDraw()}. This is
 * the one seam on which {@link SystemDice} and {@link SeededDice} differ — real entropy versus a seeded
 * sequence — so the odds interpretation and the uniform-pick mechanic live here, once.
 *
 * <p>{@link #roll(Chance)} delegates the odds interpretation back to the {@link Chance} value object (the
 * probability authority); {@link #pick(List)} owns the scale-and-clamp mechanic that was previously duplicated
 * across {@code SpawnRule.pickScene} and {@code DayPhase.pickMessage} — a draw scaled across the option count,
 * with the top of the range clamped so a draw arbitrarily close to 1 still selects the last option rather than
 * running off the end.
 */
public abstract class AbstractDice implements Dice {

    /**
     * @return the next uniform random draw in {@code [0, 1)} from this implementation's source of entropy
     */
    protected abstract double nextDraw();

    @Override
    public boolean roll(Chance chance) {
        Objects.requireNonNull(chance, "chance must not be null");
        return chance.isHitBy(nextDraw());
    }

    @Override
    public <T> T pick(List<T> options) {
        Objects.requireNonNull(options, "options must not be null");
        if (options.isEmpty()) {
            throw new IllegalArgumentException("cannot pick from an empty list");
        }
        int index = (int) (nextDraw() * options.size());
        if (index >= options.size()) {
            index = options.size() - 1;
        }
        return options.get(index);
    }
}
