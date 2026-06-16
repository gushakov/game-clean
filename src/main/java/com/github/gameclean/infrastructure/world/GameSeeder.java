package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.usecase.initialize.GameSeed;
import com.github.gameclean.core.usecase.initialize.InitializeGameInputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * The single boot-time driving (primary) adapter for the system actor: it reads the authored seed and
 * the configured starting scene, assembles the {@link GameSeed}, then fires
 * {@link InitializeGameInputPort#systemInitializesGame(GameSeed)} to
 * bring a fresh game — world, player and items — into being through the domain. It replaces the former
 * {@code WorldSeeder} + {@code PlayerSeeder} pair: the world→player order they used to imply by being
 * invoked in sequence is now a single use-case interaction, so one adapter fires one interaction.
 *
 * <p>The idempotency guards (seed-if-empty, create-if-absent), the value-object construction and the
 * transactions all live inside the use case, so {@link #seed()} on an already-initialized game is a
 * no-op — which is why it is safe to invoke unconditionally on every interactive boot.
 *
 * <p>A fresh <b>prototype</b> use case is pulled from the {@link ApplicationContext} at invocation time
 * — the cargo-clean reference idiom (see design-notes). This couples the adapter to the container API,
 * but the coupling is confined to the infrastructure ring; the core never sees Spring. (A singleton
 * must fetch the prototype per interaction rather than hold one, or the prototype scope is silently
 * defeated.)
 *
 * <p>The use case never throws (every outcome is presented to the log), so the only escape from
 * {@link #seed()} is an I/O failure reading the seed resource — which propagates to fail startup fast,
 * the right behaviour when there is no world to play in.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GameSeeder {

    private final GameSeedYamlReader reader;
    private final ApplicationContext applicationContext;
    private final GameConfigurationProperties properties;

    public void seed() throws IOException {
        Resource seed = properties.getWorld().getSeedLocation();
        String startingSceneId = properties.getPlayer().getStartingSceneId();
        log.info("[GameSeed] Initializing the game from {} with starting scene {}", seed, startingSceneId);
        GameSeed gameSeed;
        try (InputStream in = seed.getInputStream()) {
            gameSeed = reader.read(in, startingSceneId);
        }
        InitializeGameInputPort initializeGame = applicationContext.getBean(InitializeGameInputPort.class);
        initializeGame.systemInitializesGame(gameSeed);
    }
}
