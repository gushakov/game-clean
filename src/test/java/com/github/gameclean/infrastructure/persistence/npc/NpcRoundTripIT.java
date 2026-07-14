package com.github.gameclean.infrastructure.persistence.npc;

import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence round-trip for the {@code Npc} aggregate against the real, running Dockerized Postgres
 * ({@code @AutoConfigureTestDatabase(replace = NONE)}). Flyway migrates the schema at context startup (through
 * V7, which creates the {@code npc} table); the {@code @DataJdbcTest} slice rolls each test back.
 *
 * <p>It exercises what spawning and autonomous movement need end to end: an NPC inserts and is found by its
 * scene and among all NPCs (its move-chance columns surviving the round-trip); wandering moves it to another
 * scene and updates the same row in place (the version-less upsert, mirroring the player adapter — not a
 * versioned save, since NPCs are single-writer). The MapStruct mapper is pulled in via {@code @Import}.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NpcDbEntityMapperImpl.class)
class NpcRoundTripIT extends AbstractPostgresIT {

    private static final SceneId HERE = new SceneId("scn1");

    @Autowired
    private NpcSpringDataRepository repository;

    @Autowired
    private JdbcAggregateTemplate aggregateTemplate;

    @Autowired
    private NpcDbEntityMapper mapper;

    @Test
    void saveNpc_inserts_an_npc_then_finds_it_in_its_scene_and_among_all() {
        SpringNpcRepositoryAdapter adapter = new SpringNpcRepositoryAdapter(repository, aggregateTemplate, mapper);

        adapter.saveNpc(npc("npc1", HERE));

        assertThat(adapter.npcsAlreadySpawned()).isTrue();
        assertThat(adapter.findAllNpcs()).extracting(n -> n.getId().getValue()).containsExactly("npc1");
        assertThat(adapter.findNpcsInScene(HERE))
                .singleElement()
                .satisfies(npc -> {
                    assertThat(npc.getId()).isEqualTo(new NpcId("npc1"));
                    assertThat(npc.getCurrentScene()).isEqualTo(HERE);
                    // The move-chance columns survive the round-trip.
                    assertThat(npc.getMoveChance()).isEqualTo(new Chance(1, 4));
                });
    }

    @Test
    void saveNpc_moves_the_npc_and_updates_the_same_row_in_place() {
        SpringNpcRepositoryAdapter adapter = new SpringNpcRepositoryAdapter(repository, aggregateTemplate, mapper);
        adapter.saveNpc(npc("npc1", HERE));
        Npc here = adapter.findNpcsInScene(HERE).getFirst();

        adapter.saveNpc(here.moveTo(new SceneId("scn2")));

        // No longer in the old scene ...
        assertThat(adapter.findNpcsInScene(HERE)).isEmpty();
        // ... but the same row persists, now in the new scene (one row, updated in place).
        assertThat(repository.count()).isEqualTo(1);
        assertThat(adapter.findNpcsInScene(new SceneId("scn2")))
                .singleElement()
                .satisfies(npc -> assertThat(npc.getId()).isEqualTo(new NpcId("npc1")));
    }

    @Test
    void npcsAlreadySpawned_is_false_for_an_empty_world() {
        SpringNpcRepositoryAdapter adapter = new SpringNpcRepositoryAdapter(repository, aggregateTemplate, mapper);
        assertThat(adapter.npcsAlreadySpawned()).isFalse();
        assertThat(adapter.findAllNpcs()).isEmpty();
    }

    private static Npc npc(String id, SceneId scene) {
        return Npc.builder()
                .id(new NpcId(id))
                .currentScene(scene)
                .shortDescription("A hooded wanderer.")
                .fullDescription("A figure in a travel-worn hooded cloak.")
                .moveChance(new Chance(1, 4))
                .build();
    }
}
