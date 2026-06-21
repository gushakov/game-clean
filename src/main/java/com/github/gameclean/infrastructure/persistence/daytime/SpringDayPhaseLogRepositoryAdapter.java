package com.github.gameclean.infrastructure.persistence.daytime;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.daytime.DayPhaseLog;
import com.github.gameclean.core.port.persistence.DayPhaseLogRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring Data JDBC-backed implementation of {@link DayPhaseLogRepositoryOperationsOutputPort} — the driven
 * adapter for day-phase-log persistence. It hides its shape entirely: {@link DayPhaseLogDbEntity},
 * {@link DayPhaseLogDbEntityMapper}, and the world-singleton row's fixed key never cross the boundary, and
 * Spring's exceptions are wrapped in the core's port errors so callers see only domain types.
 *
 * <p><b>Optimistic locking, so {@code save} replaces the hand-rolled upsert.</b> Because
 * {@link DayPhaseLogDbEntity} carries a Spring Data {@code @Version}, plain {@code repository.save} decides
 * insert-vs-update from the version itself (a {@code 0} version is a new row) and applies the optimistic
 * check on update — so this adapter needs neither the explicit {@code existsById ? update : insert} nor the
 * {@code JdbcAggregateTemplate} that the version-less game-clock adapter uses. A write whose version the
 * store has moved past raises {@link OptimisticLockingFailureException}, which is wrapped into the core's
 * {@link OptimisticLockingError} — caught <em>before</em> the broader {@link DataAccessException}, since it
 * is a subtype, so a concurrency conflict is reported distinctly from a genuine persistence fault.
 *
 * <p>The catch on {@code findDayPhaseLog} also covers {@link InvalidDomainObjectError}: a corrupt stored row
 * (a watermark below the sentinel, a negative version) fails the validating constructor during
 * reconstitution, which is an integrity fault of this port — so it too becomes a
 * {@code PersistenceOperationsError}. (Same rationale as the game-clock and player adapters.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpringDayPhaseLogRepositoryAdapter implements DayPhaseLogRepositoryOperationsOutputPort {

    private final DayPhaseLogSpringDataRepository repository;
    private final DayPhaseLogDbEntityMapper mapper;

    @Override
    public Optional<DayPhaseLog> findDayPhaseLog() {
        try {
            return repository.findById(DayPhaseLogDbEntityMapper.SINGLETON_ID).map(mapper::toDomain);
        } catch (DataAccessException | InvalidDomainObjectError e) {
            throw new PersistenceOperationsError("Cannot load the day-phase log (unreadable or corrupt)", e);
        }
    }

    @Override
    public void saveDayPhaseLog(DayPhaseLog dayPhaseLog) {
        try {
            DayPhaseLogDbEntity saved = repository.save(mapper.toDbEntity(dayPhaseLog));
            log.debug("[Persistence] Saved day-phase log at hour {} (version {})",
                    saved.getAnnouncedThroughHour(), saved.getVersion());
        } catch (OptimisticLockingFailureException e) {
            throw new OptimisticLockingError(
                    "The day-phase log was modified concurrently (stale version)", e);
        } catch (DataAccessException e) {
            throw new PersistenceOperationsError("Cannot save the day-phase log", e);
        }
    }
}
