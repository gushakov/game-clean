package com.github.gameclean.infrastructure.persistence.scene;

import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence round-trip spike for the {@code Scene} aggregate, exercised against the real,
 * running Dockerized Postgres ({@code @AutoConfigureTestDatabase(replace = NONE)} — no embedded
 * DB). Flyway migrates the schema at context startup; the {@code @DataJdbcTest} slice wraps each
 * test in a transaction that <em>rolls back</em> by default, so the inserted rows never persist
 * (the committed Flyway DDL is the documented exception to that rollback).
 *
 * <p>Writes go through {@link JdbcAggregateTemplate#insert} — an unambiguous insert, matching the
 * insert-only {@code saveScene} the future world-construction adapter will perform; reads go
 * through the {@link SceneSpringDataRepository}. The MapStruct mapper is the only collaborator the
 * slice does not supply, so it is pulled in via {@code @Import}.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(SceneDbEntityMapperImpl.class)
class SceneRoundTripIT {

    @Autowired
    private SceneSpringDataRepository repository;

    @Autowired
    private JdbcAggregateTemplate aggregateTemplate;

    @Autowired
    private SceneDbEntityMapper mapper;

    @Test
    void persistsAndReadsBackScenesWithExitsThatReferenceEachOther() {
        // given two scenes whose exits point at one another by id
        Scene tavern = Scene.builder()
                .id(new SceneId("scn1"))
                .name("The Prancing Pony")
                .shortDescription("A cosy roadside tavern.")
                .fullDescription("A warm, low-beamed common room, thick with pipe-smoke and chatter.")
                .exits(List.of(new Exit("east", new SceneId("scn2"))))
                .build();
        Scene square = Scene.builder()
                .id(new SceneId("scn2"))
                .name("Market Square")
                .shortDescription("A bustling market square.")
                .fullDescription("Stalls crowd the cobbles; the tavern door stands open to the west.")
                .exits(List.of(new Exit("west", new SceneId("scn1"))))
                .build();

        // when persisted as aggregates (inserts) ...
        // note: the tavern's exit targets scn2 before scn2 exists. This succeeds precisely
        // because exit.target_scene_id carries no foreign key — target resolution is a domain
        // rule for the use case, not a database constraint.
        aggregateTemplate.insert(mapper.toDbEntity(tavern));
        aggregateTemplate.insert(mapper.toDbEntity(square));

        // ... and the first scene is read back and mapped to the domain
        Scene reloaded = mapper.toDomain(repository.findById("scn1").orElseThrow());

        // then every field survives the round-trip. Asserted field-by-field on purpose: Scene
        // equality is by id only, so an id-equality check would pass even on a corrupted round-trip.
        assertThat(reloaded.getId()).isEqualTo(tavern.getId());
        assertThat(reloaded.getName()).isEqualTo(tavern.getName());
        assertThat(reloaded.getShortDescription()).isEqualTo(tavern.getShortDescription());
        assertThat(reloaded.getFullDescription()).isEqualTo(tavern.getFullDescription());
        assertThat(reloaded.getExits()).containsExactlyInAnyOrderElementsOf(tavern.getExits());

        assertThat(repository.count()).isEqualTo(2);
    }
}
