package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Data JDBC-backed implementation of {@link ItemRepositoryOperationsOutputPort} — the driven adapter
 * for item persistence. It hides its shape entirely: {@link ItemDbEntity} and {@link ItemDbEntityMapper}
 * never cross the boundary, and Spring's {@link DataAccessException} is wrapped in the core's
 * {@link PersistenceOperationsError} so callers see only domain types and a single unchecked failure mode.
 *
 * <p>The catch is narrow ({@code DataAccessException}, not {@code Exception}) so a stray programming bug
 * rides raw to the use case's catch-all instead of masquerading as a persistence fault.
 * {@code findItemsInScene} additionally catches {@link InvalidDomainObjectError}: a corrupt stored row
 * fails the validating constructors during reconstitution, and that is an integrity fault of this port —
 * so it too becomes a {@code PersistenceOperationsError}. (See {@code SpringSceneRepositoryAdapter} for the
 * full rationale.)
 *
 * <p>Writes go through {@link JdbcAggregateTemplate#insert} rather than a {@code save}: item ids are
 * assigned (the generated {@code ItemId}, never null), and {@code save} would treat an assigned id as an
 * update. In this slice items are only ever spawned (inserted), never updated, so every write is
 * unambiguously an insert.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SpringItemRepositoryAdapter implements ItemRepositoryOperationsOutputPort {

    ItemSpringDataRepository repository;
    JdbcAggregateTemplate aggregateTemplate;
    ItemDbEntityMapper mapper;

    @Override
    public List<Item> findItemsInScene(SceneId sceneId) {
        try {
            return repository.findBySceneId(sceneId.getValue()).stream().map(mapper::toDomain).toList();
        } catch (DataAccessException | InvalidDomainObjectError e) {
            throw new PersistenceOperationsError(
                    "Cannot load items in scene %s (unreadable or corrupt)".formatted(sceneId.getValue()), e);
        }
    }

    @Override
    public void saveItem(Item item) {
        try {
            aggregateTemplate.insert(mapper.toDbEntity(item));
            log.debug("[Persistence] Inserted item {} in scene {}",
                    item.getId().getValue(), item.getLocation().getValue());
        } catch (DataAccessException e) {
            throw new PersistenceOperationsError("Cannot save item %s".formatted(item.getId().getValue()), e);
        }
    }

    @Override
    public boolean itemsAlreadySpawned() {
        try {
            return repository.count() > 0;
        } catch (DataAccessException e) {
            throw new PersistenceOperationsError("Cannot determine whether items have been spawned", e);
        }
    }
}
