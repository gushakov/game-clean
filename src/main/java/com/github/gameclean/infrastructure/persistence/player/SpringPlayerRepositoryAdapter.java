package com.github.gameclean.infrastructure.persistence.player;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring Data JDBC-backed implementation of {@link PlayerRepositoryOperationsOutputPort} — the driven
 * adapter for player persistence. It hides its shape entirely: {@link PlayerDbEntity} and
 * {@link PlayerDbEntityMapper} never cross the boundary, and any Spring or JDBC exception is wrapped
 * in the core's {@link PersistenceOperationsError} so callers see only domain types and a single
 * unchecked failure mode.
 *
 * <p>{@code savePlayer} is an <em>upsert</em>: player ids are assigned (never null), so Spring Data JDBC's
 * {@code save} cannot tell a new player from an existing one — it would always attempt an update. The
 * adapter decides explicitly, issuing {@link JdbcAggregateTemplate#insert} when no row exists yet (the boot
 * seeder creating the player) and {@link JdbcAggregateTemplate#update} when one does (a {@code move}
 * recording the player's new position). Optimistic locking via {@code @Version} is deliberately deferred to
 * the concurrency thread — single-session play has no contended player aggregate yet.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpringPlayerRepositoryAdapter implements PlayerRepositoryOperationsOutputPort {

    private final PlayerSpringDataRepository repository;
    private final JdbcAggregateTemplate aggregateTemplate;
    private final PlayerDbEntityMapper mapper;

    @Override
    public Optional<Player> findPlayer(PlayerId id) {
        try {
            return repository.findById(id.getValue()).map(mapper::toDomain);
        } catch (Exception e) {
            throw new PersistenceOperationsError("Cannot load player %s".formatted(id.getValue()), e);
        }
    }

    @Override
    public void savePlayer(Player player) {
        try {
            PlayerDbEntity entity = mapper.toDbEntity(player);
            if (repository.existsById(entity.getId())) {
                aggregateTemplate.update(entity);
                log.debug("[Persistence] Updated player {}", player.getId().getValue());
            } else {
                aggregateTemplate.insert(entity);
                log.debug("[Persistence] Inserted player {}", player.getId().getValue());
            }
        } catch (Exception e) {
            throw new PersistenceOperationsError(
                    "Cannot save player %s".formatted(player.getId().getValue()), e);
        }
    }
}
