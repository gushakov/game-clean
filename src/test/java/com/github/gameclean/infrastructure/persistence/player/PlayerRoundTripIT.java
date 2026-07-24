package com.github.gameclean.infrastructure.persistence.player;

import com.github.gameclean.core.model.combat.HitPoints;
import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
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
 * Persistence round-trip for the {@code Player} aggregate against the real, running Dockerized Postgres
 * ({@code @AutoConfigureTestDatabase(replace = NONE)}). Flyway migrates the schema at context startup (V12 gives
 * player its embedded {@code (hit_points, max_hit_points)} pool and its {@code @Version} column); the
 * {@code @DataJdbcTest} slice rolls each test back by default (the committed Flyway DDL is the documented
 * exception).
 *
 * <p>It exercises what player creation, {@code move}, and the NPC counterstrike need end to end: a player
 * inserts and reads back with its scene and embedded hit points intact; {@code savePlayer} updates the same row
 * in place (the path {@code move} exercises); and — now that an NPC counterstrike makes the player a contested,
 * two-writer aggregate — the optimistic lock has <b>teeth</b>: a second write carrying a version the store has
 * moved past is rejected with {@link OptimisticLockingError} rather than silently overwriting. The MapStruct
 * mapper is the only collaborator the slice does not supply, so it is pulled in via {@code @Import}.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PlayerDbEntityMapperImpl.class)
class PlayerRoundTripIT extends AbstractPostgresIT {

    @Autowired
    private PlayerSpringDataRepository repository;

    @Autowired
    private PlayerDbEntityMapper mapper;

    @Test
    void persistsAndReadsBackThePlayerWithItsSceneAndEmbeddedHitPoints() {
        SpringPlayerRepositoryAdapter adapter = new SpringPlayerRepositoryAdapter(repository, mapper);

        adapter.savePlayer(player("scn1"));

        Player reloaded = adapter.findPlayer(PlayerId.of("plr1")).orElseThrow();
        // Field-by-field on purpose: Player equality is by id only, so an id check alone would pass even if the
        // scene or the hit-point columns were corrupted on the round-trip.
        assertThat(reloaded.getId()).isEqualTo(PlayerId.of("plr1"));
        assertThat(reloaded.getCurrentScene()).isEqualTo(SceneId.of("scn1"));
        assertThat(reloaded.getHitPoints()).isEqualTo(HitPoints.full(30));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void savePlayer_inserts_a_new_player_then_updates_its_position_in_place() {
        SpringPlayerRepositoryAdapter adapter = new SpringPlayerRepositoryAdapter(repository, mapper);

        adapter.savePlayer(player("scn1"));
        Player here = adapter.findPlayer(PlayerId.of("plr1")).orElseThrow();   // carries the post-insert version

        adapter.savePlayer(here.moveTo(SceneId.of("scn2")));

        Player reloaded = adapter.findPlayer(PlayerId.of("plr1")).orElseThrow();
        // Same id, new position: the second save updated the existing row, not inserted a second one.
        assertThat(reloaded.getCurrentScene()).isEqualTo(SceneId.of("scn2"));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void savePlayer_rejects_a_stale_write_with_an_optimistic_locking_error() {
        SpringPlayerRepositoryAdapter adapter = new SpringPlayerRepositoryAdapter(repository, mapper);
        adapter.savePlayer(player("scn1"));
        Player loaded = adapter.findPlayer(PlayerId.of("plr1")).orElseThrow();   // captures the current version

        // A first write succeeds and moves the stored version past what `loaded` holds ...
        adapter.savePlayer(loaded.takeDamage(5));

        // ... so a second write still carrying the original (now stale) version is rejected — the two-actor race
        // between the player's move and an NPC's counterstrike.
        assertThatExceptionOfType(OptimisticLockingError.class)
                .isThrownBy(() -> adapter.savePlayer(loaded.moveTo(SceneId.of("scn2"))));
    }

    private static Player player(String currentScene) {
        return Player.builder()
                .id(PlayerId.of("plr1"))
                .currentScene(SceneId.of(currentScene))
                .hitPoints(HitPoints.full(30))
                .version(0)
                .build();
    }
}
