package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.port.concurrency.OptimisticLockingError;

import java.util.Optional;

/**
 * Driven (output) port for player persistence — the {@code OutputPort} suffix marks the hexagonal
 * direction: the core is the caller, an infrastructure adapter the implementor.
 *
 * <p>Scoped to what its use cases need: a lookup by id (the {@code Look} use case reads the player to
 * find its current scene; the boot seeder uses the same lookup as an existence check) and a save (the
 * boot seeder creates the single player; {@code move} updates its position). Following the established
 * driven-port convention, it trades in the domain {@link Player} model — the adapter hides its own
 * persistence shape.
 *
 * <p>Failures surface as the unchecked {@link PersistenceOperationsError}; methods declare no
 * {@code throws} so they compose directly as transaction actions if a future use case needs them to.
 */
public interface PlayerRepositoryOperationsOutputPort {

    /**
     * @return the player with the given id, or empty if none is persisted.
     * @throws PersistenceOperationsError if the lookup fails
     */
    Optional<Player> findPlayer(PlayerId id);

    /**
     * Persists the player, inserting it if new (version {@code 0}) and updating it in place otherwise, with an
     * optimistic-locking version check. The boot seeder creates the player; {@code move} updates its position;
     * an NPC counterstrike updates its hit points. Now that the player is contested (its {@code move} races an
     * NPC's counterstrike), this is a version-checked save (the {@code Npc}/{@code Item} save's twin): a write
     * carrying a version the store has moved past is rejected.
     *
     * @throws OptimisticLockingError     if the player was modified concurrently (a stale version) — reacted to
     *                                    via the transaction port's {@code onLockDetected}
     * @throws PersistenceOperationsError if the save fails otherwise
     */
    void savePlayer(Player player);
}
