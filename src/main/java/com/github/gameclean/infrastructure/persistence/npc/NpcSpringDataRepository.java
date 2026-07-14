package com.github.gameclean.infrastructure.persistence.npc;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Spring Data JDBC repository over {@link NpcDbEntity}. Infrastructure plumbing, not a domain port: the use
 * cases depend on the {@code NpcRepositoryOperationsOutputPort} instead, whose adapter delegates here.
 *
 * <p>{@link #findByCurrentSceneId(String)} is a derived query (WHERE {@code current_scene_id = ?}) backing the
 * "NPCs standing in this scene" lookup; {@code findAll} and {@code count} come from {@code CrudRepository}.
 */
public interface NpcSpringDataRepository extends CrudRepository<NpcDbEntity, String> {

    List<NpcDbEntity> findByCurrentSceneId(String sceneId);
}
