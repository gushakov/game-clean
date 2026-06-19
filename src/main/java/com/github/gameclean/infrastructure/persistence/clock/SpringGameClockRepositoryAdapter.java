package com.github.gameclean.infrastructure.persistence.clock;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.clock.GameClock;
import com.github.gameclean.core.port.persistence.GameClockRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring Data JDBC-backed implementation of {@link GameClockRepositoryOperationsOutputPort} — the driven
 * adapter for game-clock persistence. It hides its shape entirely: {@link GameClockDbEntity},
 * {@link GameClockDbEntityMapper}, and the world-singleton row's fixed key never cross the boundary, and
 * Spring's {@link DataAccessException} is wrapped in the core's {@link PersistenceOperationsError} so callers
 * see only domain types.
 *
 * <p>The catch on {@code findClock} also covers {@link InvalidDomainObjectError}: a corrupt stored row (a
 * negative banked total) fails the validating constructor during reconstitution, which is an integrity fault
 * of this port — so it too becomes a {@code PersistenceOperationsError}. (See
 * {@code SpringPlayerRepositoryAdapter} for the full rationale.)
 *
 * <p>{@code saveClock} is an <em>upsert</em> against the single fixed key
 * ({@link GameClockDbEntityMapper#SINGLETON_ID}): game initialization inserts the clock at zero, suspend
 * updates the banked total. As with the player, assigned ids mean Spring Data JDBC's {@code save} cannot tell
 * a new row from an existing one, so the adapter decides explicitly with {@code existsById}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpringGameClockRepositoryAdapter implements GameClockRepositoryOperationsOutputPort {

    private final GameClockSpringDataRepository repository;
    private final JdbcAggregateTemplate aggregateTemplate;
    private final GameClockDbEntityMapper mapper;

    @Override
    public Optional<GameClock> findClock() {
        try {
            return repository.findById(GameClockDbEntityMapper.SINGLETON_ID).map(mapper::toDomain);
        } catch (DataAccessException | InvalidDomainObjectError e) {
            throw new PersistenceOperationsError("Cannot load the game clock (unreadable or corrupt)", e);
        }
    }

    @Override
    public void saveClock(GameClock clock) {
        try {
            GameClockDbEntity entity = mapper.toDbEntity(clock);
            if (repository.existsById(entity.getId())) {
                aggregateTemplate.update(entity);
                log.debug("[Persistence] Updated game clock to {}s", clock.getAccumulatedGameSeconds());
            } else {
                aggregateTemplate.insert(entity);
                log.debug("[Persistence] Inserted game clock at {}s", clock.getAccumulatedGameSeconds());
            }
        } catch (DataAccessException e) {
            throw new PersistenceOperationsError("Cannot save the game clock", e);
        }
    }
}
