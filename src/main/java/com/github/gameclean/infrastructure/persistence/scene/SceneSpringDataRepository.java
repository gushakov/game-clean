package com.github.gameclean.infrastructure.persistence.scene;

import org.springframework.data.repository.CrudRepository;

/**
 * Spring Data JDBC repository over {@link SceneDbEntity}. This is infrastructure plumbing, not a
 * domain port: the world-construction use case will depend on the
 * {@code SceneRepositoryOperationsOutputPort} instead, whose adapter (added with that use case)
 * delegates here. For the persistence spike it is exercised directly by the round-trip test.
 */
public interface SceneSpringDataRepository extends CrudRepository<SceneDbEntity, String> {
}
