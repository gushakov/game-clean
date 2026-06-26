package com.github.gameclean.core.model.dice;

import java.util.Random;

/**
 * Deterministic {@link Dice}: a fixed seed gives a fixed sequence of draws, so the same rolls and picks recur
 * on every run. This is what preserves the testability the old randomness output port existed for — without a
 * mocked port — and it opens a reproducible-world-from-a-seed feature later (deferred for now; no seed is wired
 * into game configuration yet).
 *
 * <p>Backed by a single seeded {@link Random}, so unlike {@link SystemDice} it is <em>not</em> thread-safe: it
 * is meant for single-threaded reproducible use (a test, or a future seeded world built on one thread), not for
 * sharing across the concurrent actors that roll {@link SystemDice}.
 */
public class SeededDice extends AbstractDice {

    private final Random random;

    public SeededDice(long seed) {
        this.random = new Random(seed);
    }

    @Override
    protected double nextDraw() {
        return random.nextDouble();
    }
}
