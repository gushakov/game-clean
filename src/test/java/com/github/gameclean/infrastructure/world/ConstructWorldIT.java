package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.usecase.initialize.ConstructWorldInputPort;
import com.github.gameclean.core.usecase.initialize.ConstructWorldPresenterOutputPort;
import com.github.gameclean.core.usecase.initialize.SceneEntry;
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
import static org.mockito.Mockito.verify;

/**
 * End-to-end integration of the {@code ConstructWorld} vertical against the real Dockerized Postgres:
 * the use case (resolved through the composition root), the {@code SpringSceneRepositoryAdapter}, the
 * transaction adapter, the MapStruct mapper and the Flyway-migrated schema all participate. Only the
 * presenter is replaced — by a Mockito mock — so the use case's reported outcomes can be asserted
 * alongside the persisted state.
 *
 * <p>{@code @SpringBootTest} (not a {@code @DataJdbcTest} slice) so the programmatic transactions
 * genuinely commit — there is no test-managed rollback. Each test therefore cleans up after itself
 * in {@code @AfterEach}; deleting a scene removes its owned exits with it.
 *
 * <p>Boot 4 / Spring 7 note: {@code @MockBean} is removed; the replacement is
 * {@code @MockitoBean} from {@code org.springframework.test.context.bean.override.mockito}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ConstructWorldIT {

    private static final List<String> SEED_IDS = List.of("scn1", "scn2", "scn3", "scn4");

    @MockitoBean
    private ConstructWorldPresenterOutputPort presenter;

    @Autowired
    private ConstructWorldInputPort constructWorld;

    @Autowired
    private SceneSpringDataRepository sceneRepository;

    private final SceneYamlReader reader = new SceneYamlReader();

    @AfterEach
    void cleanUp() {
        SEED_IDS.forEach(sceneRepository::deleteById);
    }

    @Test
    void constructsAndSeedsTheAuthoredWorldThenSkipsOnASecondRun() throws Exception {
        List<SceneEntry> entries = readSeed();

        // first run against an empty world: the four authored scenes are seeded ...
        constructWorld.constructWorld(entries);

        assertThat(sceneRepository.count()).isEqualTo(4);
        ArgumentCaptor<List<Scene>> captor = sceneListCaptor();
        verify(presenter).presentSuccessfulWorldConstruction(captor.capture());
        assertThat(captor.getValue()).extracting(scene -> scene.getId().getValue())
                .containsExactlyElementsOf(SEED_IDS);

        // ... a second run finds the world already populated and skips, adding no duplicate rows.
        constructWorld.constructWorld(entries);

        assertThat(sceneRepository.count()).isEqualTo(4);
        verify(presenter).presentWorldAlreadyConstructed();
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
