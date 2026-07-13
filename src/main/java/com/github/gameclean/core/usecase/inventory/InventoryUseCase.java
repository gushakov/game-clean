package com.github.gameclean.core.usecase.inventory;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.port.persistence.ItemRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Optional;

/**
 * Lists the items the player is carrying. Implementation of {@link InventoryInputPort}; framework-free,
 * wired by the composition root, exercised in isolation against mocked ports.
 *
 * <p>Read-only like {@code look}: a resolve, a read, a presentation — no transaction, no after-commit hook.
 *
 * <p><b>The opening resolves the player inline, not through the {@code orient} subcase.</b> The prologue
 * {@code orient} factors is "resolve the acting player <em>and the scene they stand in</em>" — the opening
 * of every interaction grounded in the current scene. Inventory is grounded in the player alone: the scene
 * contributes nothing to what they carry, and a dangling current-scene reference must not block this
 * interaction. So the player half is inlined (four lines) rather than reused with a false scene dependency;
 * a resolve-player-only subcase would be speculation until a second player-only interaction exists.
 *
 * <p>On every path exactly one {@code present*} is reached: player-not-found presents and returns; success
 * presents the carried items (possibly none — the same outcome, phrased by the renderer); the outermost
 * {@code catch} routes anything unhandled (a malformed configured id, a persistence fault) to
 * {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class InventoryUseCase implements InventoryInputPort {

    InventoryPresenterOutputPort presenter;
    PlayerOperationsOutputPort playerOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    ItemRepositoryOperationsOutputPort itemOps;

    @Override
    public void playerReviewsBelongings() {
        try {
            // Initiating actor: the player — ambient, resolved here. Constructing the id value object is
            // the validity gate; a malformed configured id throws and ends at the outermost checkpoint.
            PlayerId playerId = new PlayerId(playerOps.currentPlayerId());

            Optional<Player> player = playerRepositoryOps.findPlayer(playerId);
            if (player.isEmpty()) {
                presenter.presentPlayerNotFound(playerId);
                return;
            }

            presenter.presentCarriedItems(itemOps.findItemsHeldBy(player.get().getId()));

        } catch (Exception e) {
            presenter.presentError(e);
        }
    }
}
