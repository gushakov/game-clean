package com.github.gameclean.infrastructure.persistence.npc;

import com.github.gameclean.core.model.InvalidDomainObjectError;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.NpcRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PersistenceOperationsError;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
 * <p><b>Version-less upsert, mirroring the player adapter — not the versioned item save.</b> NPCs carry no
 * {@code @Version} (single-writer today), so Spring Data JDBC's {@code save} cannot tell a new NPC from an
 * existing one — it would always attempt an update. The adapter decides explicitly, issuing
 * {@link JdbcAggregateTemplate#insert} when no row exists yet (the boot seeder spawning an NPC) and
 * {@link JdbcAggregateTemplate#update} when one does (autonomous movement recording a new position). Optimistic
 * locking is deferred until the player can affect an NPC (the trigger for a second writer).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class SpringNpcRepositoryAdapter implements NpcRepositoryOperationsOutputPort {

    NpcSpringDataRepository repository;
    JdbcAggregateTemplate aggregateTemplate;
    NpcDbEntityMapper mapper;

    @Override
    public List<Npc> findAllNpcs() {
        try {
            List<Npc> npcs = new ArrayList<>();
            repository.findAll().forEach(entity -> npcs.add(mapper.toDomain(entity)));
            return npcs;
        } catch (DataAccessException | InvalidDomainObjectError e) {
            throw new PersistenceOperationsError("Cannot load NPCs (unreadable or corrupt)", e);
        }
    }

    @Override
    public List<Npc> findNpcsInScene(SceneId sceneId) {
        try {
            return repository.findByCurrentSceneId(sceneId.getValue())
                    .stream().map(mapper::toDomain).toList();
        } catch (DataAccessException | InvalidDomainObjectError e) {
            throw new PersistenceOperationsError(
                    "Cannot load NPCs in scene %s (unreadable or corrupt)".formatted(sceneId.getValue()), e);
        }
    }

    @Override
    public void saveNpc(Npc npc) {
        try {
            NpcDbEntity entity = mapper.toDbEntity(npc);
            if (repository.existsById(entity.getId())) {
                aggregateTemplate.update(entity);
                log.debug("[Persistence] Updated npc {} (now in scene {})",
                        npc.getId().getValue(), npc.getCurrentScene().getValue());
            } else {
                aggregateTemplate.insert(entity);
                log.debug("[Persistence] Inserted npc {} (in scene {})",
                        npc.getId().getValue(), npc.getCurrentScene().getValue());
            }
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
