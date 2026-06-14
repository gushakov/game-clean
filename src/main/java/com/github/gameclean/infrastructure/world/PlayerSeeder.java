package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.usecase.initialize.CreatePlayerInputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Driving (primary) adapter for the system actor at the player-creation step: it reads the configured
 * starting scene and fires {@link CreatePlayerInputPort#createPlayer(String)} to bring the single
 * player into existence <em>through the domain</em>. A plain singleton peer of {@link WorldSeeder},
 * invoked by {@link com.github.gameclean.infrastructure.BootSequence} after the world exists.
 *
 * <p>It is deliberately symmetrical with {@link WorldSeeder}: where that adapter reads the authored
 * world seed and delegates to {@code ConstructWorld}, this one reads {@code game.player.startingSceneId}
 * and delegates to {@code CreatePlayer}. The idempotency guard, the value-object construction and the
 * transaction all live inside the use case, so {@link #seed()} on an already-created player is a no-op
 * — which is why it is safe to invoke unconditionally on every interactive boot.
 *
 * <p>A fresh <b>prototype</b> use case is pulled from the {@link ApplicationContext} at invocation
 * time — the same idiom {@link WorldSeeder} uses. This couples the adapter to the container API, but
 * the coupling is confined to the infrastructure ring; the core never sees Spring. (A singleton must
 * fetch the prototype per interaction rather than hold one, or the prototype scope is silently
 * defeated.)
 *
 * <p>The use case never throws (every outcome is presented to the log), so {@link #seed()} has no
 * escape of its own — a configuration or persistence fault surfaces as a presented {@code CreatePlayer}
 * outcome, not an exception out of here.
 */
@Component
@RequiredArgsConstructor
public class PlayerSeeder {

    private final ApplicationContext applicationContext;
    private final GameConfigurationProperties properties;

    public void seed() {
        CreatePlayerInputPort createPlayer = applicationContext.getBean(CreatePlayerInputPort.class);
        createPlayer.createPlayer(properties.getPlayer().getStartingSceneId());
    }
}
