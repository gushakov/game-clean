package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.usecase.initialize.InitializeGameInputPort;
import com.github.gameclean.core.usecase.initialize.InitializeGamePresenterOutputPort;
import com.github.gameclean.core.usecase.initialize.SceneEntry;
import com.github.gameclean.infrastructure.persistence.player.PlayerSpringDataRepository;
import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * End-to-end integration of the {@code InitializeGame} vertical against the real Dockerized Postgres:
 * the use case (resolved through the composition root), the {@code SpringSceneRepositoryAdapter} and
 * {@code SpringPlayerRepositoryAdapter}, the transaction adapter, the MapStruct mappers and the
 * Flyway-migrated schema all participate. Only the presenter is replaced — by a Mockito mock — so the
 * use case's single reported outcome can be asserted alongside the persisted state.
 *
 * <p>{@code @SpringBootTest} (not a {@code @DataJdbcTest} slice) so the programmatic transactions
 * genuinely commit — there is no test-managed rollback. The test cleans up after itself in
 * {@code @AfterEach}; deleting a scene removes its owned exits with it.
 *
 * <p>Boot 4 / Spring 7 note: {@code @MockBean} is removed; the replacement is {@code @MockitoBean} from
 * {@code org.springframework.test.context.bean.override.mockito}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InitializeGameIT {

    private static final List<String> SEED_IDS = List.of("scn1", "scn2", "scn3", "scn4");
    private static final String SEEDED_PLAYER_ID = "plr1";

    @MockitoBean
    private InitializeGamePresenterOutputPort presenter;

    @Autowired
    private InitializeGameInputPort initializeGame;

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    @Autowired
    private PlayerSpringDataRepository playerRepository;

    private final SceneYamlReader reader = new SceneYamlReader();

    @AfterEach
    void cleanUp() {
        playerRepository.deleteById(SEEDED_PLAYER_ID);
        SEED_IDS.forEach(sceneRepository::deleteById);
    }

    @Test
    void initializesTheWorldAndPlayerThenStaysIdempotentOnASecondRun() throws Exception {
        List<SceneEntry> entries = readSeed();

        // first run against an empty game: the four authored scenes are seeded and the player placed,
        // reported by the single success outcome ...
        initializeGame.systemInitializesGame(entries, "scn1");

        assertThat(sceneRepository.count()).isEqualTo(4);
        assertThat(playerRepository.findById(SEEDED_PLAYER_ID)).isPresent()
                .get().satisfies(player -> assertThat(player.getCurrentSceneId()).isEqualTo("scn1"));
        ArgumentCaptor<List<Scene>> captor = sceneListCaptor();
        verify(presenter).presentGameInitialized(captor.capture(), eq(new PlayerId(SEEDED_PLAYER_ID)));
        assertThat(captor.getValue()).extracting(scene -> scene.getId().getValue())
                .containsExactlyElementsOf(SEED_IDS);

        // ... a second run finds both already present, writes no duplicate rows, and presents the same
        // single success again.
        initializeGame.systemInitializesGame(entries, "scn1");

        assertThat(sceneRepository.count()).isEqualTo(4);
        assertThat(playerRepository.count()).isEqualTo(1);
        verify(presenter, times(2)).presentGameInitialized(any(), eq(new PlayerId(SEEDED_PLAYER_ID)));
    }

    private List<SceneEntry> readSeed() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/world/scenes.yaml")) {
            assertThat(in).as("world/scenes.yaml on the classpath").isNotNull();
            return reader.read(in);
        }
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<Scene>> sceneListCaptor() {
        return ArgumentCaptor.forClass(List.class);
    }
}
