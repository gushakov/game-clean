package com.github.gameclean.infrastructure.world;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.seed.SceneEntry;
import com.github.gameclean.infrastructure.persistence.scene.SceneDbEntityMapper;
import com.github.gameclean.infrastructure.persistence.scene.SceneDbEntityMapperImpl;
import com.github.gameclean.infrastructure.persistence.scene.SceneSpringDataRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spike round-trip for the whole authored world seed: read {@code world/scenes.yaml} into
 * {@link SceneEntry} DTOs, construct the {@link Scene} aggregates, persist the entire graph, and
 * read it back from the real Dockerized Postgres. This is the "store the graph in the DB" half of
 * the YAML spike, leaning on the persistence harness the earlier scenes spike already proved
 * (Flyway DDL + MapStruct + Spring Data JDBC); no use case or port adapter is involved yet.
 *
 * <p>Scenes are inserted in file order, so {@code scn2}'s {@code up -> scn4} exit is written before
 * {@code scn4} exists — which succeeds only because {@code exit.target_scene_id} carries no foreign
 * key. The seed is authored to exercise exactly that forward reference.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SceneDbEntityMapperImpl.class)
class WorldSeedRoundTripIT {

    @Autowired
    private SceneSpringDataRepository repository;

    @Autowired
    private JdbcAggregateTemplate aggregateTemplate;

    @Autowired
    private SceneDbEntityMapper mapper;

    private final GameSeedYamlReader reader = new GameSeedYamlReader();

    @Test
    void persistsTheAuthoredSceneGraphAndReadsItBack() throws Exception {
        // given the authored seed parsed and constructed into aggregates ...
        List<Scene> world;
        try (InputStream in = getClass().getResourceAsStream("/world/scenes.yaml")) {
            world = reader.read(in, "scn1").getScenes().stream().map(WorldSeedRoundTripIT::toScene).toList();
        }
        assertThat(world).hasSize(4);

        // ... when the whole graph is persisted in file order (forward refs and all) ...
        world.forEach(scene -> aggregateTemplate.insert(mapper.toDbEntity(scene)));

        // ... then every scene is stored, and a scene with both a back-edge and a forward-edge
        // round-trips with its exits intact.
        assertThat(repository.count()).isEqualTo(4);

        Scene courtyard = mapper.toDomain(repository.findById("scn2").orElseThrow());
        assertThat(courtyard.getName()).isEqualTo("Courtyard");
        assertThat(courtyard.getExits())
                .containsExactlyInAnyOrder(
                        new Exit("south", new SceneId("scn1")),
                        new Exit("up", new SceneId("scn4")));
    }

    private static Scene toScene(SceneEntry entry) {
        return Scene.builder()
                .id(new SceneId(entry.getId()))
                .name(entry.getName())
                .shortDescription(entry.getShortDescription())
                .fullDescription(entry.getFullDescription())
                .exits(entry.getExits().stream()
                        .map(e -> new Exit(e.getName(), new SceneId(e.getTarget())))
                        .toList())
                .build();
    }
}
