package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.scene.Exit;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerResult;
import com.github.gameclean.core.usecase.orient.OrientPlayerSubcaseInputPort;
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
 * scene — and that opening is now factored into the {@link OrientPlayerSubcaseInputPort orient subcase}
 * the two reuse. The subcase <em>returns</em> the player and their current scene on success, or presents
 * the not-found outcome and throws {@link SubcaseAlreadyPresented} on a missing player / dangling scene;
 * the dedicated checkpoint swallows that signal as a no-op. (Earlier this prologue was inline-duplicated
 * to avoid a subcase that "presents on failure and continues on success"; the guarded-prologue subcase
 * dissolves that straddle — each invocation either presents-and-throws or returns-without-presenting.)
 *
 * <p>Unlike {@code look}, {@code move} <b>writes</b>: it records the player's new position. The reads and
 * the validity checks all run <em>outside</em> any transaction; a single
 * {@link TransactionOperationsOutputPort#doInTransaction} holds only the write, and the success
 * presentation is deferred to after-commit so the player is never shown the entered room before the move
 * is durable. Exactly one {@code present*} is reached on every path and it is the interaction's last act:
 * the subcase presents the two not-found outcomes; the no-such-exit and dangling-target branches present
 * and return; the outermost {@code catch} routes anything unhandled (a malformed configured id, or a
 * {@code PersistenceOperationsError} that has already rolled back) to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class MoveUseCase implements MoveInputPort {

    MovePresenterOutputPort presenter;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    SceneRepositoryOperationsOutputPort sceneOps;
    TransactionOperationsOutputPort txOps;
    OrientPlayerSubcaseInputPort orientPlayerSubcase;

    @Override
    public void playerMovesThrough(String exitName) {
        try {
            // Shared opening: resolve the acting player and the scene they stand in (orient subcase).
            OrientPlayerResult playerInScene = orientPlayerSubcase.playerGetsBearings();

            // Match the chosen exit among the current scene's exits (case-insensitive, in the domain).
            Optional<Exit> exit = playerInScene.getScene().exitNamed(exitName);
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
            Player moved = playerInScene.getPlayer().moveTo(targetId);
            Scene entered = targetScene.get();
            txOps.doInTransaction(false, () -> {
                playerRepositoryOps.savePlayer(moved);
                txOps.doAfterCommit(() -> presenter.presentScene(entered));
            });
            return;

        } catch (SubcaseAlreadyPresented e) {
            // The orient subcase already presented its outcome (missing player or dangling scene); no-op.
        } catch (Exception e) {
            // Outermost checkpoint: a malformed configured id or a PersistenceOperationsError ends here.
            presenter.presentError(e);
        }
    }
}
