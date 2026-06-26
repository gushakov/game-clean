package com.github.gameclean.infrastructure.persistence.scene;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Spring Data JDBC-backed implementation of {@link SceneRepositoryOperationsOutputPort} — the driven
 * adapter the world-construction use case depends on. It hides its persistence shape entirely: the
 * {@link SceneDbEntity} and the {@link SceneDbEntityMapper} never cross the boundary, and Spring's
 * {@link DataAccessException} is wrapped in the core's {@link PersistenceOperationsError} so the use
 * case sees only its own domain types and a single unchecked failure mode.
 *
 * <p>The catch is narrow on purpose — {@code DataAccessException}, not {@code Exception}. A stray
 * programming bug (e.g. an {@code NullPointerException}) is <em>not</em> a persistence fault and rides
 * raw to the use case's outermost catch-all, rather than being mislabelled "Cannot load/save scene".
 * On the read path the catch additionally takes {@link InvalidDomainObjectError}: reconstitution rebuilds
 * the aggregate through its validating constructors, so a <em>corrupt stored row</em> fails there — and a
 * corrupt row is an integrity fault of this port (the store is valid by provenance), so it too becomes a
 * {@code PersistenceOperationsError}, deliberately not a domain-input invalidity.
 *
 * <p>Writes go through {@link JdbcAggregateTemplate#insert} rather than a {@code save}: scene ids are
 * authored (assigned), and {@code save} would treat an assigned id as an update. Construction seeds
 * an empty world, so every write is unambiguously an insert.
 *
 * <p>A plain {@code @Component}: all its collaborators are already beans (the Spring Data repository,
 * the {@code JdbcAggregateTemplate}, the generated MapStruct mapper), so no explicit {@code @Bean}
 * declaration is needed — unlike the transaction adapter, whose {@code TransactionTemplate}s had to
 * be declared by hand.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SpringSceneRepositoryAdapter implements SceneRepositoryOperationsOutputPort {

    SceneSpringDataRepository repository;
    JdbcAggregateTemplate aggregateTemplate;
    SceneDbEntityMapper mapper;

    @Override
    public boolean worldIsEmpty() {
        try {
            return repository.count() == 0;
        } catch (DataAccessException e) {
            throw new PersistenceOperationsError("Cannot determine whether the world is empty", e);
        }
    }

    @Override
    public void saveScene(Scene scene) {
        try {
            aggregateTemplate.insert(mapper.toDbEntity(scene));
            log.debug("[Persistence] Inserted scene {}", scene.getId().getValue());
        } catch (DataAccessException e) {
            throw new PersistenceOperationsError(
                    "Cannot save scene %s".formatted(scene.getId().getValue()), e);
        }
    }

    @Override
    public Optional<Scene> findScene(SceneId id) {
        try {
            return repository.findById(id.getValue()).map(mapper::toDomain);
        } catch (DataAccessException | InvalidDomainObjectError e) {
            throw new PersistenceOperationsError(
                    "Cannot load scene %s (unreadable or corrupt)".formatted(id.getValue()), e);
        }
    }
}
