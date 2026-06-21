package com.github.gameclean.infrastructure.persistence.daytime;

import com.github.gameclean.core.model.daytime.DayPhaseLog;
import com.github.gameclean.core.port.persistence.OptimisticLockingError;
import com.github.gameclean.infrastructure.AbstractPostgresIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jdbc.test.autoconfigure.DataJdbcTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Persistence round-trip for the {@code DayPhaseLog} aggregate against the real, running Dockerized Postgres
 * ({@code @AutoConfigureTestDatabase(replace = NONE)}). Flyway migrates the schema at context startup; the
 * {@code @DataJdbcTest} slice rolls each test back, so inserted rows never persist.
 *
 * <p>Because the entity carries a Spring Data {@code @Version}, the adapter's {@code saveDayPhaseLog} is a
 * plain version-driven upsert: a {@code 0}-version instance inserts; a loaded instance updates with an
 * optimistic check. The tests drive that end to end against the single world-singleton row — insert at the
 * sentinel, advance the watermark, read it back — and prove the lock has <b>teeth</b>: a write carrying a
 * version the store has moved past is rejected with {@link OptimisticLockingError} rather than overwriting the
 * newer state. The MapStruct mapper is the only collaborator the slice does not supply, so it is pulled in via
 * {@code @Import}.
 */
@DataJdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(DayPhaseLogDbEntityMapperImpl.class)
class DayPhaseLogRoundTripIT extends AbstractPostgresIT {

    @Autowired
    private DayPhaseLogSpringDataRepository repository;

    @Autowired
    private DayPhaseLogDbEntityMapper mapper;

    @Test
    void saveDayPhaseLog_inserts_at_the_sentinel_then_advances_the_watermark_in_place() {
        SpringDayPhaseLogRepositoryAdapter adapter = new SpringDayPhaseLogRepositoryAdapter(repository, mapper);

        // Initialization inserts the log at the "nothing announced" sentinel (version 0 → a new row) ...
        adapter.saveDayPhaseLog(DayPhaseLog.initial());
        DayPhaseLog loaded = adapter.findDayPhaseLog().orElseThrow();
        assertThat(loaded.getAnnouncedThroughHour()).isEqualTo(-1);

        // ... an announcement advances the watermark in place, checked against the version we loaded.
        adapter.saveDayPhaseLog(loaded.announceThrough(6));

        assertThat(adapter.findDayPhaseLog()).get()
                .extracting(DayPhaseLog::getAnnouncedThroughHour).isEqualTo(6L);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void saveDayPhaseLog_rejects_a_stale_write_with_an_optimistic_locking_error() {
        SpringDayPhaseLogRepositoryAdapter adapter = new SpringDayPhaseLogRepositoryAdapter(repository, mapper);

        adapter.saveDayPhaseLog(DayPhaseLog.initial());
        DayPhaseLog loaded = adapter.findDayPhaseLog().orElseThrow();   // captures the current version

        // A first advance succeeds and moves the stored version past what `loaded` holds ...
        adapter.saveDayPhaseLog(loaded.announceThrough(6));

        // ... so a second write still carrying the original (now stale) version is rejected, not applied —
        // exactly what stops two concurrent observers from both announcing.
        assertThatExceptionOfType(OptimisticLockingError.class)
                .isThrownBy(() -> adapter.saveDayPhaseLog(loaded.announceThrough(18)));
        assertThat(adapter.findDayPhaseLog()).get()
                .extracting(DayPhaseLog::getAnnouncedThroughHour).isEqualTo(6L);   // the stale 18 never landed
    }

    @Test
    void findDayPhaseLog_isEmpty_whenTheGameHasNotBeenInitialized() {
        SpringDayPhaseLogRepositoryAdapter adapter = new SpringDayPhaseLogRepositoryAdapter(repository, mapper);

        assertThat(adapter.findDayPhaseLog()).isEmpty();
    }
}
