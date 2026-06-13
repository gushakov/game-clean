package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.usecase.initialize.ConstructWorldInputPort;
import com.github.gameclean.core.usecase.initialize.SceneEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Driving (primary) adapter for the system actor: it reads the authored seed and fires
 * {@link ConstructWorldInputPort#constructWorld(List)} to build the world through the domain. The
 * idempotency guard lives inside the use case, so {@link #seed()} on an already-populated world is a
 * no-op — which is why it is safe to invoke unconditionally on every interactive boot.
 *
 * <p>It is a plain singleton, no longer an {@code ApplicationRunner}: the boot <em>order</em> (seed
 * the world before the player can act in it) is owned by {@link com.github.gameclean.infrastructure.BootSequence},
 * the single runner that sequences the startup steps explicitly. Pulling that ordering out of Spring's
 * runner-sorting SPI and into one readable place is the whole point.
 *
 * <p>A fresh <b>prototype</b> use case is pulled from the {@link ApplicationContext} at invocation
 * time — the cargo-clean reference idiom (see design-notes). This couples the adapter to the container
 * API, but the coupling is confined to the infrastructure ring; the core never sees Spring. (A
 * singleton must fetch the prototype per interaction rather than hold one, or the prototype scope is
 * silently defeated.)
 *
 * <p>The use case never throws (every outcome is presented), so the only escape from {@link #seed()}
 * is an I/O failure reading the seed resource — which propagates to fail startup fast, the right
 * behaviour when there is no world to play in.
 */
@Component
@EnableConfigurationProperties(WorldSeedProperties.class)
@RequiredArgsConstructor
@Slf4j
public class WorldSeeder {

    private final SceneYamlReader reader;
    private final ApplicationContext applicationContext;
    private final WorldSeedProperties properties;

    public void seed() throws IOException {
        Resource seed = properties.getSeedLocation();
        log.info("[WorldSeed] Constructing the world from {}", seed);
        List<SceneEntry> entries;
        try (InputStream in = seed.getInputStream()) {
            entries = reader.read(in);
        }
        ConstructWorldInputPort constructWorld = applicationContext.getBean(ConstructWorldInputPort.class);
        constructWorld.constructWorld(entries);
    }
}
