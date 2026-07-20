package com.github.gameclean.infrastructure.persistence.npc;

import org.springframework.data.repository.CrudRepository;

import java.util.List;

/**
 * Spring Data JDBC repository over {@link NpcDbEntity}. Infrastructure plumbing, not a domain port: the use
 * cases depend on the {@code NpcRepositoryOperationsOutputPort} instead, whose adapter delegates here.
 *
 * <p>The two derived queries filter on {@code hit_points > ?}: a dead NPC (0 hit points) stays in the table
 * but is gone from every listing and from targeting, so callers pass {@code 0} to mean "living only".
 * {@link #findByCurrentSceneIdAndCurrentHitPointsGreaterThan(String, int)} backs the "living NPCs standing in
 * this scene" lookup; {@link #findByCurrentHitPointsGreaterThan(int)} backs the ticker's "every living NPC"
 * enumeration. {@code count} (all rows, including the dead) comes from {@code CrudRepository} and backs the
 * spawn-if-none guard — a world that spawned NPCs is already seeded even if they have since died.
 */
public interface NpcSpringDataRepository extends CrudRepository<NpcDbEntity, String> {

    List<NpcDbEntity> findByCurrentSceneIdAndCurrentHitPointsGreaterThan(String sceneId, int hitPoints);

    List<NpcDbEntity> findByCurrentHitPointsGreaterThan(int hitPoints);
}
