package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.usecase.initialize.ConstructWorldInputPort;
import com.github.gameclean.core.usecase.initialize.SceneEntry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Driving (primary) adapter that constructs the world at application startup — the system-actor peer
 * of the future terminal command adapter. It reads the authored seed and fires
 * {@link ConstructWorldInputPort#constructWorld(List)}; the idempotency guard lives inside the use
 * case, so re-running a populated world is a no-op.
 *
 * <p>Guarded by {@code game.world.construct-on-startup}: the bean exists only when that flag is
 * {@code true} (set in the application profile), so test slices — which leave it {@code false} —
 * never seed the shared database by accident. A fresh <b>prototype</b> use case is pulled from the
 * {@link ApplicationContext} at invocation time — the cargo-clean reference idiom (see design-notes).
 * This couples the adapter to the container API, but the coupling is confined to the infrastructure
 * ring; the core never sees Spring. (A singleton must fetch the prototype per interaction rather than
 * hold one, or the prototype scope is silently defeated.)
 *
 * <p>The use case never throws (every outcome is presented), so the only escape from {@link #run}
 * is an I/O failure reading the seed resource — which fails startup fast, the right behaviour when
 * there is no world to play in.
 *
 * <p>Ordered {@link Ordered#HIGHEST_PRECEDENCE} so it seeds the world <em>before</em> the blocking
 * {@code ConsoleInputLoop} runner takes the main thread; otherwise the loop would block first and the
 * world would never be seeded.
 */
@Component
@ConditionalOnProperty(prefix = "game.world", name = "construct-on-startup", havingValue = "true")
@EnableConfigurationProperties(WorldSeedProperties.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
@Slf4j
public class WorldSeedRunner implements ApplicationRunner {

    private final SceneYamlReader reader;
    private final ApplicationContext applicationContext;
    private final WorldSeedProperties properties;

    @Override
    public void run(ApplicationArguments args) throws Exception {
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
