package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.usecase.initialize.GameSeed;
import com.github.gameclean.core.usecase.initialize.InitializeGameInputPort;
import com.github.gameclean.infrastructure.persistence.item.ItemSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.player.PlayerSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration of the {@code InitializeGame} vertical against the real Dockerized Postgres,
 * driven directly through the composition root with explicitly-read seed entries: the use case, the
 * {@code SpringSceneRepositoryAdapter} and {@code SpringPlayerRepositoryAdapter}, the transaction adapter,
 * the MapStruct mappers and the Flyway-migrated schema all participate. The outcome is asserted through the
 * <em>persisted state</em>: the presenter is no longer a bean (the composition root {@code new}s it), so it
 * cannot be replaced by a mock here. The presenter-outcome contract is pinned by
 * {@code InitializeGameUseCaseTest}; this test proves the real writes and their idempotency across two runs.
 *
 * <p>{@code @SpringBootTest} (not a {@code @DataJdbcTest} slice) so the programmatic transactions genuinely
 * commit — there is no test-managed rollback. The test cleans up after itself in {@code @AfterEach};
 * deleting a scene removes its owned exits with it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InitializeGameIT {

    private static final List<String> SEED_IDS = List.of("scn1", "scn2", "scn3", "scn4");
    private static final String SEEDED_PLAYER_ID = "plr1";

    @Autowired
    private InitializeGameInputPort initializeGame;

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    @Autowired
    private PlayerSpringDataRepository playerRepository;

    @Autowired
    private ItemSpringDataRepository itemRepository;

    private final GameSeedYamlReader reader = new GameSeedYamlReader();

    @AfterEach
    void cleanUp() {
        itemRepository.deleteAll();
        playerRepository.deleteById(SEEDED_PLAYER_ID);
        SEED_IDS.forEach(sceneRepository::deleteById);
    }

    @Test
    void initializesTheWorldPlayerAndItemsThenStaysIdempotentOnASecondRun() throws Exception {
        GameSeed seed = readSeed();

        // first run against an empty game: the four authored scenes are seeded, the player placed, and the
        // authored items spawned (a random count, but deterministically idempotent) ...
        initializeGame.systemInitializesGame(seed);

        assertThat(sceneRepository.count()).isEqualTo(4);
        assertThat(sceneRepository.findById("scn2")).isPresent();
        assertThat(playerRepository.findById(SEEDED_PLAYER_ID)).isPresent()
                .get().satisfies(player -> assertThat(player.getCurrentSceneId()).isEqualTo("scn1"));
        long itemsAfterFirstRun = itemRepository.count();

        // ... a second run finds world, player and items already present and writes no duplicate rows —
        // notably the spawn is not re-rolled, so the item count is unchanged.
        initializeGame.systemInitializesGame(seed);

        assertThat(sceneRepository.count()).isEqualTo(4);
        assertThat(playerRepository.count()).isEqualTo(1);
        assertThat(itemRepository.count()).isEqualTo(itemsAfterFirstRun);
    }

    private GameSeed readSeed() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/world/scenes.yaml")) {
            assertThat(in).as("world/scenes.yaml on the classpath").isNotNull();
            return reader.read(in, "scn1");
        }
    }
}
