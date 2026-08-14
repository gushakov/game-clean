package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.npc.NpcId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;

import java.util.List;
import java.util.Optional;

/**
 * Driven (output) port for NPC persistence — the use case drives it; an infrastructure adapter implements it.
 * The {@code OutputPort} suffix marks the hexagonal direction: the core is the caller, the infrastructure ring
 * the implementor.
 *
 * <p>Scoped to what its use cases need: the living NPCs (the animate use case enumerates them each tick), the
 * living NPCs in a scene ({@code look}/{@code move} list them and {@code hit} targets them), a per-NPC save
 * (spawning inserts, movement and strikes update), a per-NPC delete (a slaying replaces the NPC row with its
 * corpse item), and an emptiness check for the spawn idempotency guard. Following the established driven-port
 * convention, it trades in the domain {@link Npc} model — the adapter hides its own persistence shape.
 *
 * <p><b>The dead vanish from the reads.</b> A slain NPC is {@linkplain #deleteNpc(Npc) deleted} (its corpse
 * item takes over the scene presence), and the two {@code find} methods additionally exclude any zero-hit-point
 * row as defense in depth: the dead are gone from listings and from targeting either way.
 *
 * <p>Failures surface as the unchecked {@link PersistenceOperationsError}, caught at the use-case checkpoint;
 * methods declare no {@code throws} so they compose directly as transaction actions.
 */
public interface NpcRepositoryOperationsOutputPort {

    /**
     * @return every living NPC in the world, empty if none. The animate use case enumerates them each tick and
     *         rolls each one's move chance.
     * @throws PersistenceOperationsError if the lookup fails
     */
    List<Npc> findAllNpcs();

    /**
     * @return the living NPCs currently standing in the given scene, empty if none. {@code look}/{@code move}
     *         list them when they present that scene, and {@code hit} resolves its target among them.
     * @throws PersistenceOperationsError if the lookup fails
     */
    List<Npc> findNpcsInScene(SceneId sceneId);

    /**
     * @return the living NPC with the given id, or empty if none is persisted or it is dead. The NPC
     *         counterstrike ({@code FightNpc.npcStrikesPlayer}) loads its striker by id; a gone-or-dead NPC
     *         yields an empty result, which the interaction treats as a quiet no-op (the loop re-derives).
     * @throws PersistenceOperationsError if the lookup fails
     */
    Optional<Npc> findNpc(NpcId id);

    /**
     * Persists a single NPC, inserting it if new (version {@code 0}) and updating it in place otherwise, with an
     * optimistic-locking version check. The boot seeder spawns NPCs; autonomous movement updates a wanderer's
     * position; a strike updates its hit points. Now that the player can affect an NPC, this is a
     * version-checked save (the {@code Item} save's twin): a write carrying a version the store has moved past
     * is rejected.
     *
     * @throws OptimisticLockingError     if the NPC was modified concurrently (a stale version) — reacted to via
     *                                    the transaction port's {@code onLockDetected}
     * @throws PersistenceOperationsError if the save fails otherwise
     */
    void saveNpc(Npc npc);

    /**
     * Deletes a single NPC, checked against the optimistic-locking version the given aggregate carries — the
     * write a slaying performs: the NPC row is removed and its corpse item is saved in the same transaction.
     * Version-checked like {@link #saveNpc(Npc)}, and for the same reason: the lethal strike races the
     * wandering/attacking policy's executions, and the loser must not delete an NPC another writer has since
     * moved past (the strike would have missed).
     *
     * @throws OptimisticLockingError     if the NPC was modified concurrently (a stale version) — reacted to via
     *                                    the transaction port's {@code onLockDetected}
     * @throws PersistenceOperationsError if the delete fails otherwise
     */
    void deleteNpc(Npc npc);

    /**
     * @return {@code true} once at least one NPC has been spawned into the world. The NPC-spawning idempotency
     *         guard relies on this — inside the seed transaction — to decide whether spawning is still needed,
     *         so a restart never re-rolls placements. <b>Known consequence of deleting the slain:</b> once every
     *         NPC in the world has been slain (all rows deleted), this reads {@code false} again and the next
     *         boot re-spawns fresh instances — a deliberate trade (the fallen leave corpses; the world
     *         repopulates) rather than an idempotency bug.
     * @throws PersistenceOperationsError if the check fails
     */
    boolean npcsAlreadySpawned();
}
