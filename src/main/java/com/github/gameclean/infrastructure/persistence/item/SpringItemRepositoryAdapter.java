package com.github.gameclean.infrastructure.persistence.item;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Spring Data JDBC-backed implementation of {@link ItemRepositoryOperationsOutputPort} — the driven adapter
 * for item persistence. It hides its shape entirely: {@link ItemDbEntity} and {@link ItemDbEntityMapper}
 * never cross the boundary, and Spring's exceptions are wrapped in the core's port errors so callers see only
 * domain types.
 *
 * <p>The catch is narrow ({@code DataAccessException}, not {@code Exception}) so a stray programming bug
 * rides raw to the use case's catch-all instead of masquerading as a persistence fault.
 * {@code findItemsInScene} additionally catches {@link InvalidDomainObjectError}: a corrupt stored row
 * fails the validating constructors during reconstitution, and that is an integrity fault of this port —
 * so it too becomes a {@code PersistenceOperationsError}. (See {@code SpringSceneRepositoryAdapter} for the
 * full rationale.)
 *
 * <p><b>Optimistic locking, so {@code save} replaces the {@code JdbcAggregateTemplate.insert}.</b> Since
 * {@link ItemDbEntity} now carries a Spring Data {@code @Version}, plain {@code repository.save} decides
 * insert-vs-update from the version itself (a {@code 0} version is a new row, so spawn-at-init still inserts)
 * and applies the optimistic check on update — the same shape as the day-phase-log adapter. A write whose
 * version the store has moved past raises {@link OptimisticLockingFailureException}, wrapped into the core's
 * {@link OptimisticLockingError} (caught <em>before</em> the broader {@link DataAccessException}, since it is a
 * subtype) so the {@code take} use case can present "someone got there first" distinctly from a genuine fault.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SpringItemRepositoryAdapter implements ItemRepositoryOperationsOutputPort {

    ItemSpringDataRepository repository;
    ItemDbEntityMapper mapper;

    @Override
    public List<Item> findItemsInScene(SceneId sceneId) {
        try {
            return repository.findByLocationKindAndLocationRef(ItemLocationKind.GROUND, sceneId.getValue())
                    .stream().map(mapper::toDomain).toList();
        } catch (DataAccessException | InvalidDomainObjectError e) {
            throw new PersistenceOperationsError(
                    "Cannot load items in scene %s (unreadable or corrupt)".formatted(sceneId.getValue()), e);
        }
    }

    @Override
    public void saveItem(Item item) {
        try {
            ItemDbEntity saved = repository.save(mapper.toDbEntity(item));
            log.debug("[Persistence] Saved item {} (version {})", saved.getId(), saved.getVersion());
        } catch (OptimisticLockingFailureException e) {
            throw new OptimisticLockingError(
                    "Item %s was modified concurrently (stale version)".formatted(item.getId().getValue()), e);
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
