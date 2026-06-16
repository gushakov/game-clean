package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.port.seed.GameSeed;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsError;
import com.github.gameclean.core.port.seed.GameSeedSourceOperationsOutputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Driven adapter implementing {@link GameSeedSourceOperationsOutputPort} over the authored YAML seed: it
 * resolves the configured seed location and starting scene, opens the resource, and hands the stream to
 * {@link GameSeedYamlReader} to assemble the {@link GameSeed}. This is where all access to the YAML parsing
 * machinery lives — confined to the infrastructure ring, behind the port the use case pulls through.
 *
 * <p>The reader stays a separate collaborator (it owns the parse + authoring-syntax normalization); this
 * adapter owns the <em>sourcing</em> — config resolution, resource I/O, and translating an {@link IOException}
 * into the unchecked {@link GameSeedSourceOperationsError} the port contract declares. A missing or broken
 * seed therefore reaches the use case's outermost checkpoint and is presented, rather than failing startup
 * from inside this adapter — uniform with how a persistence fault is handled.
 *
 * <p>It does <b>not</b> touch the domain model — no {@code Scene}, no {@code ItemId}: it returns the
 * possibly-invalid {@code *Entry} carriers, leaving the validity gate to the use case.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class YamlGameSeedSource implements GameSeedSourceOperationsOutputPort {

    private final GameSeedYamlReader reader;
    private final GameConfigurationProperties properties;

    @Override
    public GameSeed loadGameSeed() {
        Resource seed = properties.getWorld().getSeedLocation();
        String startingSceneId = properties.getPlayer().getStartingSceneId();
        log.info("[GameSeed] Loading the authored seed from {} with starting scene {}", seed, startingSceneId);
        try (InputStream in = seed.getInputStream()) {
            return reader.read(in, startingSceneId);
        } catch (IOException e) {
            throw new GameSeedSourceOperationsError(
                    "could not read the game seed from %s".formatted(seed), e);
        }
    }
}
