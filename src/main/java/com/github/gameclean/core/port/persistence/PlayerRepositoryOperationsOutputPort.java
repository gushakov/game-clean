package com.github.gameclean.core.port.persistence;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;

import java.util.Optional;

/**
 * Driven (output) port for player persistence — the {@code OutputPort} suffix marks the hexagonal
 * direction: the core is the caller, an infrastructure adapter the implementor.
 *
 * <p>Scoped to what the first player-facing round needs: a lookup by id (the {@code Look} use case
 * reads the player to find its current scene; the boot seeder uses the same lookup as an existence
 * check) and a save (the boot seeder creates the single player). Following the established
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
     * Persists a single player (insert). Ids are assigned, so this is always an insert.
     *
     * @throws PersistenceOperationsError if the save fails
     */
    void savePlayer(Player player);
}
