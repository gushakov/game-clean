package com.github.gameclean.infrastructure.world;

import com.github.gameclean.infrastructure.persistence.player.PlayerSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the single boot-time driving adapter against the real Dockerized Postgres: {@link GameSeeder}
 * reads the authored seed and the configured starting scene and brings the whole game — world plus
 * player — into being through the full stack (the {@code InitializeGame} use case, the persistence and
 * transaction adapters, the mappers, the Flyway schema). The committed seed defines four scenes and the
 * configured player starts in {@code scn1}.
 *
 * <p>The terminal runtime is left disabled (the default for tests), so {@code BootSequence} is absent
 * and nothing seeds or blocks at startup; this test invokes {@link GameSeeder#seed()} directly — the
 * exact seam {@code BootSequence} calls — and a second time to prove idempotency (the use case's
 * seed-if-empty and create-if-absent guards). No test-managed rollback under {@code @SpringBootTest}, so
 * {@code @AfterEach} clears the seeded rows — deleting a scene removes its owned exits with it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class GameSeederIT {

    private static final String SEEDED_PLAYER_ID = "plr1";
    private static final List<String> SEED_SCENE_IDS = List.of("scn1", "scn2", "scn3", "scn4");

    @Autowired
    private GameSeeder gameSeeder;

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    @Autowired
    private PlayerSpringDataRepository playerRepository;

    @AfterEach
    void cleanUp() {
        playerRepository.deleteById(SEEDED_PLAYER_ID);
        SEED_SCENE_IDS.forEach(sceneRepository::deleteById);
    }

    @Test
    void seedsTheWorldAndPlayerThroughTheFullStackAndIsIdempotent() throws Exception {
        gameSeeder.seed();
        gameSeeder.seed(); // second call must be a no-op, not a duplicate insert

        assertThat(sceneRepository.count()).isEqualTo(4);
        assertThat(sceneRepository.findById("scn2")).isPresent();
        assertThat(playerRepository.count()).isEqualTo(1);
        assertThat(playerRepository.findById(SEEDED_PLAYER_ID)).isPresent()
                .get().satisfies(player -> assertThat(player.getCurrentSceneId()).isEqualTo("scn1"));
    }
}
