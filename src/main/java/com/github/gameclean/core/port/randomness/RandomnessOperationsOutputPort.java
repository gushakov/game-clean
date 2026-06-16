package com.github.gameclean.core.port.randomness;

import com.github.gameclean.core.model.item.Chance;

/**
 * Driven (output) port supplying random draws for <em>domain</em> decisions — the core asks for a draw;
 * an infrastructure adapter backs it with a real random source. The {@code OutputPort} suffix marks the
 * hexagonal direction.
 *
 * <p>Item spawning is non-deterministic, yet the use case must stay deterministic under test, so the
 * entropy is injected through this port rather than read from a static {@code Math.random()}. Stubbing it
 * with a fixed sequence of draws makes the whole spawn phase reproducible.
 *
 * <p>This is a different role from {@link com.github.gameclean.core.port.id.IdGeneratorOperationsOutputPort}:
 * that port produces opaque <em>identity</em>; this one produces <em>domain dice</em>. Keeping them apart
 * stops "give me an identity" and "roll for an outcome" being conflated into one capability.
 */
public interface RandomnessOperationsOutputPort {

    /**
     * @return a uniform random draw in {@code [0, 1)} — interpreted by {@link Chance#isHitBy(double)} for a
     *         spawn roll, and scaled (multiplied by a bound, floored) to pick among candidate scenes.
     */
    double nextDouble();
}
