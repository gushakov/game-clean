package com.github.gameclean.infrastructure.persistence.clock;

import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Persistence round-trip for the {@code GameClock} aggregate against the real, running Dockerized Postgres
 * ({@code @AutoConfigureTestDatabase(replace = NONE)}). Flyway migrates the schema at context startup; the
 * {@code @DataJdbcTest} slice rolls each test back, so inserted rows never persist.
 *
 * <p>The test drives the adapter's {@code saveClock} upsert end to end against the single world-clock row:
 * insert at zero (initialization), then in-place update to a higher banked total (the path suspend
 * exercises), reading the banked seconds back through {@code findClock}. The MapStruct mapper is the only
 * collaborator the slice does not supply, so it is pulled in via {@code @Import}.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(GameClockDbEntityMapperImpl.class)
class GameClockRoundTripIT extends AbstractPostgresIT {

    @Autowired
    private GameClockSpringDataRepository repository;

    @Autowired
    private JdbcAggregateTemplate aggregateTemplate;

    @Autowired
    private GameClockDbEntityMapper mapper;

    @Test
    void saveClock_inserts_the_clock_then_banks_more_time_in_place() {
        SpringGameClockRepositoryAdapter adapter =
                new SpringGameClockRepositoryAdapter(repository, aggregateTemplate, mapper);

        // Initialization inserts the clock at zero ...
        adapter.saveClock(GameClock.initial());
        assertThat(adapter.findClock()).contains(GameClock.initial());

        // ... a later suspend updates the single row in place to the higher banked total.
        adapter.saveClock(new GameClock(3_600));

        assertThat(adapter.findClock()).contains(new GameClock(3_600));
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void findClock_isEmpty_whenTheGameHasNotBeenInitialized() {
        SpringGameClockRepositoryAdapter adapter =
                new SpringGameClockRepositoryAdapter(repository, aggregateTemplate, mapper);

        assertThat(adapter.findClock()).isEmpty();
    }
}
