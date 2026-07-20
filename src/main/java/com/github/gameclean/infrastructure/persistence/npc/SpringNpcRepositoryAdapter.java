package com.github.gameclean.infrastructure.persistence.npc;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
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
 * Spring Data JDBC-backed implementation of {@link NpcRepositoryOperationsOutputPort} — the driven adapter for
 * NPC persistence. It hides its shape entirely: {@link NpcDbEntity} and {@link NpcDbEntityMapper} never cross
 * the boundary, and Spring's exceptions are wrapped in the core's port errors so callers see only domain types.
 *
 * <p>The catch is narrow ({@code DataAccessException}, not {@code Exception}) so a stray programming bug rides
 * raw to the use case's catch-all instead of masquerading as a persistence fault. The read methods additionally
 * catch {@link InvalidDomainObjectError}: a corrupt stored row fails the validating constructors during
 * reconstitution, and that is an integrity fault of this port — so it too becomes a {@code PersistenceOperationsError}.
 * (See {@code SpringSceneRepositoryAdapter} for the full rationale.)
 *
 * <p><b>Versioned save, mirroring the item adapter.</b> Now that the player's {@code hit} makes NPCs a
 * two-writer aggregate (alongside the wandering ticker), the {@code @Version} on {@link NpcDbEntity} lets plain
 * {@link NpcSpringDataRepository#save} decide insert-vs-update — a {@code 0} version inserts (the boot seeder
 * spawning an NPC), a positive one updates (a strike or a wander). A stale write is rejected with Spring's
 * {@link OptimisticLockingFailureException}, caught <em>before</em> the broader {@code DataAccessException} and
 * translated to the core's {@link OptimisticLockingError} so the transaction adapter can fire an
 * {@code onLockDetected} reaction (the loser's "the target got away").
 *
 * <p>The two reads return <b>living</b> NPCs only (hit points {@code > 0}): a dead NPC stays in the table but
 * is gone from listings and targeting. {@link #npcsAlreadySpawned()} counts <em>all</em> rows on purpose — a
 * world that spawned NPCs is already seeded even if they have since died, so a restart never re-rolls them.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SpringNpcRepositoryAdapter implements NpcRepositoryOperationsOutputPort {

    NpcSpringDataRepository repository;
    NpcDbEntityMapper mapper;

    @Override
    public List<Npc> findAllNpcs() {
        try {
            return repository.findByCurrentHitPointsGreaterThan(0)
                    .stream().map(mapper::toDomain).toList();
        } catch (DataAccessException | InvalidDomainObjectError e) {
            throw new PersistenceOperationsError("Cannot load NPCs (unreadable or corrupt)", e);
        }
    }

    @Override
    public List<Npc> findNpcsInScene(SceneId sceneId) {
        try {
            return repository.findByCurrentSceneIdAndCurrentHitPointsGreaterThan(sceneId.getValue(), 0)
                    .stream().map(mapper::toDomain).toList();
        } catch (DataAccessException | InvalidDomainObjectError e) {
            throw new PersistenceOperationsError(
                    "Cannot load NPCs in scene %s (unreadable or corrupt)".formatted(sceneId.getValue()), e);
        }
    }

    @Override
    public void saveNpc(Npc npc) {
        try {
            NpcDbEntity saved = repository.save(mapper.toDbEntity(npc));
            log.debug("[Persistence] Saved npc {} (version {}, in scene {})",
                    saved.getId(), saved.getVersion(), npc.getCurrentScene().getValue());
        } catch (OptimisticLockingFailureException e) {
            throw new OptimisticLockingError(
                    "Npc %s was modified concurrently (stale version)".formatted(npc.getId().getValue()), e);
        } catch (DataAccessException e) {
            throw new PersistenceOperationsError("Cannot save npc %s".formatted(npc.getId().getValue()), e);
        }
    }

    @Override
    public boolean npcsAlreadySpawned() {
        try {
            return repository.count() > 0;
        } catch (DataAccessException e) {
            throw new PersistenceOperationsError("Cannot determine whether NPCs have been spawned", e);
        }
    }
}
