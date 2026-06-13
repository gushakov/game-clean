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
 * <p>Writes go through {@link JdbcAggregateTemplate#insert} rather than a {@code save}: player ids are
 * assigned, and {@code save} would treat an assigned id as an update. The seeder creates the player
 * exactly once into an empty store, so the write is unambiguously an insert.
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
            aggregateTemplate.insert(mapper.toDbEntity(player));
            log.debug("[Persistence] Inserted player {}", player.getId().getValue());
        } catch (Exception e) {
            throw new PersistenceOperationsError(
                    "Cannot save player %s".formatted(player.getId().getValue()), e);
        }
    }
}
