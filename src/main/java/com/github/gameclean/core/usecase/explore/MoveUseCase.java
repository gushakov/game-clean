package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Optional;

/**
 * Moves the player to an adjacent scene through a named exit, then describes the scene they enter.
 * Implementation of {@link MoveInputPort}; framework-free, wired by the composition root, exercised in
 * isolation against mocked ports.
 *
 * <p>It shares {@code look}'s opening exactly — resolve the ambient player, read it, resolve the current
 * scene — reaching the same not-found outcomes by <em>branch-and-present</em>. That shared prologue is
 * deliberately <b>not</b> factored into a subcase: on failure it presents and stops, on success it
 * continues, which is the present-or-continue straddle the single-presentation rule forbids. So it stays
 * inline; what is shared with {@code look} is the presenter <em>vocabulary</em> and its rendering, never
 * this logic.
 *
 * <p>Unlike {@code look}, {@code move} <b>writes</b>: it records the player's new position. The reads and
 * the validity checks all run <em>outside</em> any transaction; a single
 * {@link TransactionOperationsOutputPort#doInTransaction} holds only the write, and the success
 * presentation is deferred to after-commit so the player is never shown the entered room before the move
 * is durable. Exactly one {@code present*} is reached on every path and it is the interaction's last act:
 * the four not-found branches present and return; the outermost {@code catch} routes anything unhandled
 * (a malformed configured id, a {@code PersistenceOperationsError} that has already rolled back) to
 * {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class MoveUseCase implements MoveInputPort {

    MovePresenterOutputPort presenter;
    PlayerOperationsOutputPort playerOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    SceneRepositoryOperationsOutputPort sceneOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void playerMovesThrough(String exitName) {
        try {
            // Initiating actor: the player — ambient, resolved here rather than passed in.
            // Construct the id value object — the validity gate.
            PlayerId id = new PlayerId(playerOps.currentPlayerId());

            // Shared prologue (inline, not a subcase): read the player and resolve where they stand.
            // Reads run outside any transaction.
            Optional<Player> player = playerRepositoryOps.findPlayer(id);
            if (player.isEmpty()) {
                presenter.presentPlayerNotFound(id);
                return;
            }

            SceneId currentSceneId = player.get().getCurrentScene();
            Optional<Scene> currentScene = sceneOps.findScene(currentSceneId);
            if (currentScene.isEmpty()) {
                presenter.presentCurrentSceneNotFound(currentSceneId);
                return;
            }

            // Match the chosen exit among the current scene's exits (case-insensitive, in the domain).
            Optional<Exit> exit = currentScene.get().exitNamed(exitName);
            if (exit.isEmpty()) {
                presenter.presentNoSuchExit(exitName);
                return;
            }

            // Resolve the exit's target — an inter-aggregate reference that may dangle.
            SceneId targetId = exit.get().getTarget();
            Optional<Scene> targetScene = sceneOps.findScene(targetId);
            if (targetScene.isEmpty()) {
                presenter.presentTargetSceneNotFound(targetId);
                return;
            }

            // One write, one atomic unit. Record the new position; present the entered scene only once the
            // move has committed, and end the interaction there — nothing runs past a presentation.
            Player moved = player.get().moveTo(targetId);
            Scene entered = targetScene.get();
            txOps.doInTransaction(false, () -> {
                playerRepositoryOps.savePlayer(moved);
                txOps.doAfterCommit(() -> presenter.presentScene(entered));
            });
            return;

        } catch (Exception e) {
            // Outermost checkpoint: a malformed configured id or a PersistenceOperationsError ends here.
            presenter.presentError(e);
        }
    }
}
