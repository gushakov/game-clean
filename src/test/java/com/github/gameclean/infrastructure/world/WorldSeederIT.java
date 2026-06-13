package com.github.gameclean.infrastructure.world;

import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the world-seeding driving adapter against the real Dockerized Postgres: {@link WorldSeeder}
 * reads the authored seed and constructs the world through the full stack — use case, persistence
 * adapter, transaction adapter, mapper, Flyway schema. The committed seed defines four scenes.
 *
 * <p>The terminal runtime is left disabled (the default for tests), so {@code BootSequence} is absent
 * and nothing seeds or blocks at startup; this test invokes {@link WorldSeeder#seed()} directly — the
 * exact seam {@code BootSequence} calls — which keeps the seeding path covered end-to-end without a
 * console. No test-managed rollback under {@code @SpringBootTest}, so {@code @AfterEach} clears the
 * seeded rows — deleting a scene removes its owned exits with it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WorldSeederIT {

    private static final List<String> SEED_IDS = List.of("scn1", "scn2", "scn3", "scn4");

    @Autowired
    private WorldSeeder worldSeeder;

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    @AfterEach
    void cleanUp() {
        SEED_IDS.forEach(sceneRepository::deleteById);
    }

    @Test
    void seedsTheAuthoredWorldThroughTheFullStack() throws Exception {
        worldSeeder.seed();

        assertThat(sceneRepository.count()).isEqualTo(4);
        assertThat(sceneRepository.findById("scn2")).isPresent();
    }
}
