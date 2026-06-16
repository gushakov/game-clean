package com.github.gameclean.infrastructure.persistence.item;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Spring Data JDBC repository over {@link ItemDbEntity}. Infrastructure plumbing, not a domain port: the
 * use cases depend on the {@code ItemRepositoryOperationsOutputPort} instead, whose adapter delegates here.
 *
 * <p>{@link #findBySceneId(String)} is a derived query (WHERE {@code scene_id = ?}) backing the
 * "items on the ground in this scene" lookup.
 */
public interface ItemSpringDataRepository extends CrudRepository<ItemDbEntity, String> {

    List<ItemDbEntity> findBySceneId(String sceneId);
}
