package com.github.gameclean.infrastructure.world;

import com.github.gameclean.infrastructure.persistence.player.PlayerSpringDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the player-seeding driving adapter against the real Dockerized Postgres: {@link PlayerSeeder}
 * constructs the single configured player at its starting scene through the persistence port and
 * adapter, and is idempotent. The terminal runtime is left disabled (the default for tests), so
 * {@code BootSequence} is absent and nothing seeds at startup; this test invokes {@link PlayerSeeder#seed()}
 * directly — the seam {@code BootSequence} calls. No test-managed rollback under {@code @SpringBootTest},
 * so {@code @AfterEach} clears the seeded player.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PlayerSeederIT {

    private static final String SEEDED_PLAYER_ID = "plr1";

    @Autowired
    private PlayerSeeder playerSeeder;

    @Autowired
    private PlayerSpringDataRepository playerRepository;

    @AfterEach
    void cleanUp() {
        playerRepository.deleteById(SEEDED_PLAYER_ID);
    }

    @Test
    void seedsTheConfiguredPlayerOnceAndIsIdempotent() {
        playerSeeder.seed();
        playerSeeder.seed(); // second call must be a no-op, not a duplicate insert

        assertThat(playerRepository.count()).isEqualTo(1);
        assertThat(playerRepository.findById(SEEDED_PLAYER_ID)).isPresent()
                .get()
                .satisfies(player -> assertThat(player.getCurrentSceneId()).isEqualTo("scn1"));
    }
}
