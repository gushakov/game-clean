package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.item.ItemId;
import com.github.gameclean.core.model.item.Location;
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
 * Persistence round-trip for the {@code Item} aggregate against the real, running Dockerized Postgres
 * ({@code @AutoConfigureTestDatabase(replace = NONE)}). Flyway migrates the schema at context startup (through
 * V6, which gives item its {@code (location_kind, location_ref)} pair and {@code @Version} column); the
 * {@code @DataJdbcTest} slice rolls each test back.
 *
 * <p>It exercises what {@code take} needs end to end: a ground item inserts and is found by its scene; taking
 * it moves it off the ground (a {@code GROUND}→{@code HELD} location change) and updates in place; and the
 * optimistic lock has <b>teeth</b> — a second write carrying a version the store has moved past is rejected
 * with {@link OptimisticLockingError} rather than silently overwriting, which is exactly what stops two actors
 * both taking the same item. The MapStruct mapper is pulled in via {@code @Import}.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ItemDbEntityMapperImpl.class)
class ItemRoundTripIT extends AbstractPostgresIT {

    private static final SceneId HERE = new SceneId("scn1");

    @Autowired
    private ItemSpringDataRepository repository;

    @Autowired
    private ItemDbEntityMapper mapper;

    @Test
    void saveItem_inserts_a_ground_item_then_finds_it_in_its_scene() {
        SpringItemRepositoryAdapter adapter = new SpringItemRepositoryAdapter(repository, mapper);

        adapter.saveItem(groundItem("itm1", "A rusty dagger."));

        assertThat(adapter.findItemsInScene(HERE))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.getId()).isEqualTo(new ItemId("itm1"));
                    assertThat(item.getLocation()).isEqualTo(new Location.OnGround(HERE));
                });
    }

    @Test
    void taking_an_item_moves_it_off_the_ground_and_updates_in_place() {
        SpringItemRepositoryAdapter adapter = new SpringItemRepositoryAdapter(repository, mapper);
        adapter.saveItem(groundItem("itm1", "A rusty dagger."));
        Item onGround = adapter.findItemsInScene(HERE).getFirst();   // carries the post-insert version

        adapter.saveItem(onGround.takenBy(new PlayerId("plr1")));

        // No longer on the ground here ...
        assertThat(adapter.findItemsInScene(HERE)).isEmpty();
        // ... but the same row persists, now held by the player (one row, updated in place).
        assertThat(repository.count()).isEqualTo(1);
        ItemDbEntity stored = repository.findById("itm1").orElseThrow();
        assertThat(stored.getLocationKind()).isEqualTo(ItemLocationKind.HELD);
        assertThat(stored.getLocationRef()).isEqualTo("plr1");
    }

    @Test
    void saveItem_rejects_a_stale_write_with_an_optimistic_locking_error() {
        SpringItemRepositoryAdapter adapter = new SpringItemRepositoryAdapter(repository, mapper);
        adapter.saveItem(groundItem("itm1", "A rusty dagger."));
        Item loaded = adapter.findItemsInScene(HERE).getFirst();   // captures the current version

        // A first take succeeds and moves the stored version past what `loaded` holds ...
        adapter.saveItem(loaded.takenBy(new PlayerId("plr1")));

        // ... so a second write still carrying the original (now stale) version is rejected — the two-actor race.
        assertThatExceptionOfType(OptimisticLockingError.class)
                .isThrownBy(() -> adapter.saveItem(loaded.takenBy(new PlayerId("plr2"))));
    }

    private static Item groundItem(String id, String shortDescription) {
        return Item.builder()
                .id(new ItemId(id))
                .location(new Location.OnGround(HERE))
                .shortDescription(shortDescription)
                .fullDescription("A longer description of the item.")
                .build();
    }
}
