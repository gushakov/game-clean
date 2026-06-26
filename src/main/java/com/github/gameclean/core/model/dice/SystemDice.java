package com.github.gameclean.core.model.dice;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Production {@link Dice}: real entropy from the JDK's {@link ThreadLocalRandom}. Holds no state, and reads
 * {@code ThreadLocalRandom.current()} afresh on every draw, so a single instance is safe to share across the
 * threads that roll game dice (the boot/seeder thread initializing the world, the background ticker thread
 * announcing day phases). This is the one clearly-labelled home of non-determinism; all the odds and selection
 * <em>meaning</em> lives in the model ({@link Chance}, the {@code roll}/{@code pick} mechanics on
 * {@link AbstractDice}), so only this class is non-deterministic.
 */
public class SystemDice extends AbstractDice {

    @Override
    protected double nextDraw() {
        return ThreadLocalRandom.current().nextDouble();
    }
}
