package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.infrastructure.GameConfigurationProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Boot-time seeder for the single player — a plain singleton peer of {@link WorldSeeder}, invoked by
 * {@link com.github.gameclean.infrastructure.BootSequence} after the world exists. {@link #seed()}
 * creates the configured player at its starting scene if no player is persisted yet, and is otherwise
 * a no-op, so it is safe to run on every interactive boot.
 *
 * <p><strong>Deliberate deviation — seeding bypasses the use-case + transaction pattern.</strong>
 * Unlike scene seeding (which fires the {@code ConstructWorld} use case), this adapter constructs the
 * {@code Player} aggregate and writes it through the persistence port <em>directly</em>: no use case,
 * no explicit transaction demarcation. The value-object construction that normally lives inside a use
 * case happens here instead. This is a conscious trade-off (fewer moving parts for the first player
 * round) and is sound only because seeding is <em>boot-time, single-threaded and idempotent</em> —
 * the existence check then insert cannot race. It must graduate into a real {@code CreatePlayer} use
 * case the moment player creation becomes player-facing or concurrent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlayerSeeder {

    private final PlayerRepositoryOperationsOutputPort playerOps;
    private final GameConfigurationProperties properties;

    public void seed() {
        PlayerId playerId = new PlayerId(properties.getPlayer().getId());
        if (playerOps.findPlayer(playerId).isPresent()) {
            log.info("[PlayerSeed] Player {} already exists — skipping", playerId.getValue());
            return;
        }
        SceneId startingScene = new SceneId(properties.getPlayer().getStartingSceneId());
        Player player = Player.builder().id(playerId).currentScene(startingScene).build();
        playerOps.savePlayer(player);
        log.info("[PlayerSeed] Created player {} at scene {}",
                playerId.getValue(), startingScene.getValue());
    }
}
