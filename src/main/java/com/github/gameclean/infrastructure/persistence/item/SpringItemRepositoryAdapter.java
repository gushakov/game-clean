package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Data JDBC-backed implementation of {@link ItemRepositoryOperationsOutputPort} — the driven adapter
 * for item persistence. It hides its shape entirely: {@link ItemDbEntity} and {@link ItemDbEntityMapper}
 * never cross the boundary, and any Spring or JDBC exception is wrapped in the core's
 * {@link PersistenceOperationsError} so callers see only domain types and a single unchecked failure mode.
 *
 * <p>Writes go through {@link JdbcAggregateTemplate#insert} rather than a {@code save}: item ids are
 * assigned (the generated {@code ItemId}, never null), and {@code save} would treat an assigned id as an
 * update. In this slice items are only ever spawned (inserted), never updated, so every write is
 * unambiguously an insert.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpringItemRepositoryAdapter implements ItemRepositoryOperationsOutputPort {

    private final ItemSpringDataRepository repository;
    private final JdbcAggregateTemplate aggregateTemplate;
    private final ItemDbEntityMapper mapper;

    @Override
    public List<Item> findItemsInScene(SceneId sceneId) {
        try {
            return repository.findBySceneId(sceneId.getValue()).stream().map(mapper::toDomain).toList();
        } catch (Exception e) {
            throw new PersistenceOperationsError(
                    "Cannot load items in scene %s".formatted(sceneId.getValue()), e);
        }
    }

    @Override
    public void saveItem(Item item) {
        try {
            aggregateTemplate.insert(mapper.toDbEntity(item));
            log.debug("[Persistence] Inserted item {} in scene {}",
                    item.getId().getValue(), item.getLocation().getValue());
        } catch (Exception e) {
            throw new PersistenceOperationsError("Cannot save item %s".formatted(item.getId().getValue()), e);
        }
    }

    @Override
    public boolean itemsAlreadySpawned() {
        try {
            return repository.count() > 0;
        } catch (Exception e) {
            throw new PersistenceOperationsError("Cannot determine whether items have been spawned", e);
        }
    }
}
