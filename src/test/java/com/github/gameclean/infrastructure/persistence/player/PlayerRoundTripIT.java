package com.github.gameclean.infrastructure.persistence.player;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
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
 * Persistence round-trip for the {@code Player} aggregate against the real, running Dockerized
 * Postgres ({@code @AutoConfigureTestDatabase(replace = NONE)}). Flyway migrates the schema at context
 * startup; the {@code @DataJdbcTest} slice rolls each test back by default, so inserted rows never
 * persist (the committed Flyway DDL is the documented exception).
 *
 * <p>The raw round-trip writes through {@link JdbcAggregateTemplate#insert} and reads through
 * {@link PlayerSpringDataRepository}; a second test drives the adapter's {@code savePlayer} upsert end to
 * end (insert then in-place update — the path {@code move} exercises). The MapStruct mapper is the only
 * collaborator the slice does not supply, so it is pulled in via {@code @Import}.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PlayerDbEntityMapperImpl.class)
class PlayerRoundTripIT extends AbstractPostgresIT {

    @Autowired
    private PlayerSpringDataRepository repository;

    @Autowired
    private JdbcAggregateTemplate aggregateTemplate;

    @Autowired
    private PlayerDbEntityMapper mapper;

    @Test
    void persistsAndReadsBackThePlayerWithItsCurrentScene() {
        Player player = Player.builder()
                .id(new PlayerId("plr1"))
                .currentScene(new SceneId("scn1"))
                .build();

        aggregateTemplate.insert(mapper.toDbEntity(player));

        Player reloaded = mapper.toDomain(repository.findById("plr1").orElseThrow());

        // Field-by-field on purpose: Player equality is by id only, so an id check alone would pass
        // even if the current scene were corrupted on the round-trip.
        assertThat(reloaded.getId()).isEqualTo(player.getId());
        assertThat(reloaded.getCurrentScene()).isEqualTo(player.getCurrentScene());
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void savePlayer_inserts_a_new_player_then_updates_its_position_in_place() {
        SpringPlayerRepositoryAdapter adapter =
                new SpringPlayerRepositoryAdapter(repository, aggregateTemplate, mapper);

        adapter.savePlayer(Player.builder()
                .id(new PlayerId("plr1")).currentScene(new SceneId("scn1")).build());
        // Same id, new position: the second save must update the existing row, not insert a second one.
        adapter.savePlayer(Player.builder()
                .id(new PlayerId("plr1")).currentScene(new SceneId("scn2")).build());

        Player reloaded = adapter.findPlayer(new PlayerId("plr1")).orElseThrow();
        assertThat(reloaded.getCurrentScene()).isEqualTo(new SceneId("scn2"));
        assertThat(repository.count()).isEqualTo(1);
    }
}
