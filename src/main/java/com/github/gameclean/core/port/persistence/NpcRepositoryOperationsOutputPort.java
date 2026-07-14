package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.scene.SceneId;

import java.util.List;

/**
 * Driven (output) port for NPC persistence — the use case drives it; an infrastructure adapter implements it.
 * The {@code OutputPort} suffix marks the hexagonal direction: the core is the caller, the infrastructure ring
 * the implementor.
 *
 * <p>Scoped to what its use cases need: all NPCs (the animate use case enumerates them each tick), the NPCs in
 * a scene ({@code look}/{@code move} list them when presenting that scene), a per-NPC save (spawning inserts,
 * autonomous movement updates), and an emptiness check for the spawn idempotency guard. Following the
 * established driven-port convention, it trades in the domain {@link Npc} model — the adapter hides its own
 * persistence shape.
 *
 * <p>Failures surface as the unchecked {@link PersistenceOperationsError}, caught at the use-case checkpoint;
 * methods declare no {@code throws} so they compose directly as transaction actions.
 */
public interface NpcRepositoryOperationsOutputPort {

    /**
     * @return every NPC in the world, empty if none. The animate use case enumerates all NPCs each tick and
     *         rolls each one's move chance.
     * @throws PersistenceOperationsError if the lookup fails
     */
    List<Npc> findAllNpcs();

    /**
     * @return the NPCs currently standing in the given scene, empty if none. {@code look}/{@code move} list
     *         them when they present that scene.
     * @throws PersistenceOperationsError if the lookup fails
     */
    List<Npc> findNpcsInScene(SceneId sceneId);

    /**
     * Persists a single NPC, inserting it if new and updating it in place otherwise (an upsert). The boot
     * seeder spawns NPCs; autonomous movement updates a wanderer's position. NPCs carry no optimistic-locking
     * version (single-writer today), so this is the version-less upsert, not a version-checked save.
     *
     * @throws PersistenceOperationsError if the save fails
     */
    void saveNpc(Npc npc);

    /**
     * @return {@code true} once at least one NPC has been spawned into the world. The NPC-spawning idempotency
     *         guard relies on this — inside the seed transaction — to decide whether spawning is still needed,
     *         so a restart never re-rolls placements.
     * @throws PersistenceOperationsError if the check fails
     */
    boolean npcsAlreadySpawned();
}
