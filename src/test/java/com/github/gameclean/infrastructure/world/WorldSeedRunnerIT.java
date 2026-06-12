package com.github.gameclean.infrastructure.world;

import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the boot-time driving adapter against the real Dockerized Postgres: with
 * {@code game.world.construct-on-startup=true}, the {@link WorldSeedRunner} fires at context startup
 * (before any test method) and seeds the authored world through the full stack — use case, persistence
 * adapter, transaction adapter, mapper, Flyway schema. The committed seed defines four scenes.
 *
 * <p>The {@code properties} override gives this class its own cached context, so the seeder does not
 * run in {@code ConstructWorldIT} / {@code TransactionOperationsIT} (whose contexts leave the flag
 * {@code false}). No test-managed rollback under {@code @SpringBootTest}, so {@code @AfterEach} clears
 * the seeded rows — deleting a scene removes its owned exits with it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "game.world.construct-on-startup=true")
class WorldSeedRunnerIT {

    private static final List<String> SEED_IDS = List.of("scn1", "scn2", "scn3", "scn4");

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    @AfterEach
    void cleanUp() {
        SEED_IDS.forEach(sceneRepository::deleteById);
    }

    @Test
    void seedsTheAuthoredWorldAtStartup() {
        assertThat(sceneRepository.count()).isEqualTo(4);
        assertThat(sceneRepository.findById("scn2")).isPresent();
    }
}
