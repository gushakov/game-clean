package com.github.gameclean.infrastructure.randomness;

import com.github.gameclean.core.port.randomness.RandomnessOperationsOutputPort;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Driven adapter backing {@link RandomnessOperationsOutputPort} with the JDK's
 * {@link ThreadLocalRandom} — the real source of entropy for item-spawn rolls. The use case interprets the
 * draws through the {@code Chance} and {@code SpawnRule} value objects, so all the domain logic stays
 * deterministic under test (the port is stubbed with a fixed sequence); only this adapter is
 * non-deterministic. {@code nextDouble()} returns a uniform draw in {@code [0, 1)}, the contract the port
 * documents.
 */
@Component
public class JdkRandomnessAdapter implements RandomnessOperationsOutputPort {

    @Override
    public double nextDouble() {
        return ThreadLocalRandom.current().nextDouble();
    }
}
