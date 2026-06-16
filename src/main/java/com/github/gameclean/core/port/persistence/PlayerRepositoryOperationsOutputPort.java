package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;

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
     * Persists the player, inserting it if new and updating it in place otherwise (an upsert). The boot
     * seeder creates the player; {@code move} updates its position. The adapter hides the insert-vs-update
     * decision, so callers express only intent — "persist this player".
     *
     * @throws PersistenceOperationsError if the save fails
     */
    void savePlayer(Player player);
}
