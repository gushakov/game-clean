package com.github.gameclean.infrastructure.persistence.scene;

import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;

/**
 * Spring Data JDBC-backed implementation of {@link SceneRepositoryOperationsOutputPort} — the driven
 * adapter the world-construction use case depends on. It hides its persistence shape entirely: the
 * {@link SceneDbEntity} and the {@link SceneDbEntityMapper} never cross the boundary, and any Spring
 * or JDBC exception is wrapped in the core's {@link PersistenceOperationsError} so the use case sees
 * only its own domain types and a single unchecked failure mode.
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
public class SpringSceneRepositoryAdapter implements SceneRepositoryOperationsOutputPort {

    private final SceneSpringDataRepository repository;
    private final JdbcAggregateTemplate aggregateTemplate;
    private final SceneDbEntityMapper mapper;

    @Override
    public boolean worldIsEmpty() {
        try {
            return repository.count() == 0;
        } catch (Exception e) {
            throw new PersistenceOperationsError("Cannot determine whether the world is empty", e);
        }
    }

    @Override
    public void saveScene(Scene scene) {
        try {
            aggregateTemplate.insert(mapper.toDbEntity(scene));
            log.debug("[Persistence] Inserted scene {}", scene.getId().getValue());
        } catch (Exception e) {
            throw new PersistenceOperationsError(
                    "Cannot save scene %s".formatted(scene.getId().getValue()), e);
        }
    }
}
