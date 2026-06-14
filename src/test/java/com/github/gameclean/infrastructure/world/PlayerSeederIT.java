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
 * Verifies the player-creation driving adapter against the real Dockerized Postgres: {@link PlayerSeeder}
 * fires the {@code CreatePlayer} use case, which constructs the single configured player at its starting
 * scene through the persistence and transaction adapters, and is idempotent. The terminal runtime is
 * left disabled (the default for tests), so {@code BootSequence} is absent and nothing seeds at startup;
 * this test invokes {@link PlayerSeeder#seed()} directly — the seam {@code BootSequence} calls.
 *
 * <p>The world is seeded first, via {@link WorldSeeder#seed()}, exactly as {@code BootSequence} orders
 * it: {@code CreatePlayer} enforces the inter-aggregate rule that the player's starting scene must
 * resolve to a persisted scene, so without the world there would be no {@code scn1} to start in and no
 * player would be created. No test-managed rollback under {@code @SpringBootTest}, so {@code @AfterEach}
 * clears the seeded player and scenes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PlayerSeederIT {

    private static final String SEEDED_PLAYER_ID = "plr1";
    private static final List<String> SEED_SCENE_IDS = List.of("scn1", "scn2", "scn3", "scn4");

    @Autowired
    private WorldSeeder worldSeeder;

    @Autowired
    private PlayerSeeder playerSeeder;

    @Autowired
    private PlayerSpringDataRepository playerRepository;

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    @AfterEach
    void cleanUp() {
        playerRepository.deleteById(SEEDED_PLAYER_ID);
        SEED_SCENE_IDS.forEach(sceneRepository::deleteById);
    }

    @Test
    void createsTheConfiguredPlayerOnceAndIsIdempotent() throws Exception {
        worldSeeder.seed();  // the starting scene must exist before the player can be created in it

        playerSeeder.seed();
        playerSeeder.seed(); // second call must be a no-op, not a duplicate insert

        assertThat(playerRepository.count()).isEqualTo(1);
        assertThat(playerRepository.findById(SEEDED_PLAYER_ID)).isPresent()
                .get()
                .satisfies(player -> assertThat(player.getCurrentSceneId()).isEqualTo("scn1"));
    }
}
