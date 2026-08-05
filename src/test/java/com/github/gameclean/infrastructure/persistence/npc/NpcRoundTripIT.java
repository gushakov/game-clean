package com.github.gameclean.infrastructure.persistence.npc;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.dice.Chance;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Persistence round-trip for the {@code Npc} aggregate against the real, running Dockerized Postgres
 * ({@code @AutoConfigureTestDatabase(replace = NONE)}). Flyway migrates the schema at context startup (V8 gives
 * npc its {@code (hit_points, max_hit_points)} pair and {@code @Version} column; V10 collapses the move chance
 * into a single {@code num/den} text column; V11 adds the {@code hostile} stance and the {@code attack_chance}
 * text column); the {@code @DataJdbcTest} slice rolls each test back.
 *
 * <p>It exercises what spawning, autonomous movement, and {@code hit} need end to end: an NPC inserts and is
 * found by its scene and among all NPCs (its move-chance and hit-point columns surviving the round-trip);
 * wandering moves it and updates the same row in place; a dead NPC (zero hit points) stays in the table but
 * vanishes from both reads; and the optimistic lock has <b>teeth</b> — a second write carrying a version the
 * store has moved past is rejected with {@link OptimisticLockingError} rather than silently overwriting, which
 * is what stops the player's {@code hit} and the ticker both winning a race. The MapStruct mapper is pulled in
 * via {@code @Import}.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NpcDbEntityMapperImpl.class)
class NpcRoundTripIT extends AbstractPostgresIT {

    private static final SceneId HERE = SceneId.of("scn1");

    @Autowired
    private NpcSpringDataRepository repository;

    @Autowired
    private NpcDbEntityMapper mapper;

    @Test
    void saveNpc_inserts_an_npc_then_finds_it_in_its_scene_and_among_all() {
        SpringNpcRepositoryAdapter adapter = new SpringNpcRepositoryAdapter(repository, mapper);

        adapter.saveNpc(npc("npc1", HERE));

        assertThat(adapter.npcsAlreadySpawned()).isTrue();
        assertThat(adapter.findAllNpcs()).extracting(n -> n.getId().asString()).containsExactly("npc1");
        assertThat(adapter.findNpcsInScene(HERE))
                .singleElement()
                .satisfies(npc -> {
                    assertThat(npc.getId()).isEqualTo(NpcId.of("npc1"));
                    assertThat(npc.getCurrentScene()).isEqualTo(HERE);
                    // The move-chance, attack-chance, hostile-stance and hit-point columns survive the round-trip.
                    assertThat(npc.getMoveChance()).isEqualTo(new Chance(1, 4));
                    assertThat(npc.getAttackChance()).isEqualTo(new Chance(1, 3));
                    assertThat(npc.isHostile()).isFalse();
                    assertThat(npc.getHitPoints()).isEqualTo(HitPoints.full(10));
                });
    }

    @Test
    void saveNpc_moves_the_npc_and_updates_the_same_row_in_place() {
        SpringNpcRepositoryAdapter adapter = new SpringNpcRepositoryAdapter(repository, mapper);
        adapter.saveNpc(npc("npc1", HERE));
        Npc here = adapter.findNpcsInScene(HERE).getFirst();   // carries the post-insert version

        adapter.saveNpc(here.moveTo(SceneId.of("scn2")));

        // No longer in the old scene ...
        assertThat(adapter.findNpcsInScene(HERE)).isEmpty();
        // ... but the same row persists, now in the new scene (one row, updated in place).
        assertThat(repository.count()).isEqualTo(1);
        assertThat(adapter.findNpcsInScene(SceneId.of("scn2")))
                .singleElement()
                .satisfies(npc -> assertThat(npc.getId()).isEqualTo(NpcId.of("npc1")));
    }

    @Test
    void a_dead_npc_stays_in_the_table_but_vanishes_from_both_reads() {
        SpringNpcRepositoryAdapter adapter = new SpringNpcRepositoryAdapter(repository, mapper);
        adapter.saveNpc(npc("npc1", HERE));
        Npc alive = adapter.findNpcsInScene(HERE).getFirst();   // carries the post-insert version

        adapter.saveNpc(alive.takeDamage(10));   // to zero hit points

        // Gone from listings and targeting ...
        assertThat(adapter.findNpcsInScene(HERE)).isEmpty();
        assertThat(adapter.findAllNpcs()).isEmpty();
        // ... but the row persists (corpse deferred), so the world still counts as seeded.
        assertThat(repository.count()).isEqualTo(1);
        assertThat(adapter.npcsAlreadySpawned()).isTrue();
    }

    @Test
    void npcsAlreadySpawned_is_false_for_an_empty_world() {
        SpringNpcRepositoryAdapter adapter = new SpringNpcRepositoryAdapter(repository, mapper);
        assertThat(adapter.npcsAlreadySpawned()).isFalse();
        assertThat(adapter.findAllNpcs()).isEmpty();
    }

    @Test
    void saveNpc_rejects_a_stale_write_with_an_optimistic_locking_error() {
        SpringNpcRepositoryAdapter adapter = new SpringNpcRepositoryAdapter(repository, mapper);
        adapter.saveNpc(npc("npc1", HERE));
        Npc loaded = adapter.findNpcsInScene(HERE).getFirst();   // captures the current version

        // A first write succeeds and moves the stored version past what `loaded` holds ...
        adapter.saveNpc(loaded.takeDamage(3));

        // ... so a second write still carrying the original (now stale) version is rejected — the two-actor race
        // between the player's hit and the wandering ticker.
        assertThatExceptionOfType(OptimisticLockingError.class)
                .isThrownBy(() -> adapter.saveNpc(loaded.moveTo(SceneId.of("scn2"))));
    }

    private static Npc npc(String id, SceneId scene) {
        return Npc.builder()
                .id(NpcId.of(id))
                .currentScene(scene)
                .shortDescription("A hooded wanderer.")
                .fullDescription("A figure in a travel-worn hooded cloak.")
                .moveChance(new Chance(1, 4))
                .attackChance(new Chance(1, 3))
                .hitPoints(HitPoints.full(10))
                .build();
    }
}
