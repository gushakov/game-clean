package com.github.gameclean.core.usecase.initialize;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.transaction.TransactionOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

/**
 * Creates and persists the single player at its starting scene. Implementation of
 * {@link CreatePlayerInputPort}; framework-free, wired by the composition root, exercised in isolation
 * against mocked ports.
 *
 * <p>This is the graduation of what was once a boot seeder reaching straight into a persistence
 * output port: player creation is a system interaction in its own right, so it earns a use case
 * symmetrical with {@link ConstructWorldUseCase}. The actor is the <em>system at startup</em>, so
 * there is no security assertion; the single interaction follows the methodology's checkpoint
 * structure, transaction-tight:
 * <ol>
 *   <li><b>Build</b> the {@link PlayerId} (ambient, from {@code playerOps}), the starting
 *       {@link SceneId} (the authored carrier) and the {@link Player} aggregate — the validity gate
 *       for a malformed configured id.</li>
 *   <li><b>Resolve</b> the starting scene — the <em>inter-aggregate</em> rule that no entity can own:
 *       the player's current scene must point at a scene that exists. Surfaced as a domain outcome,
 *       not a dangling reference.</li>
 *   <li><b>Create once</b> inside one transaction — the existence guard and the write share it, so
 *       the create-if-absent decision and its effect are atomic; presentation is deferred to
 *       after-commit so success is never reported before the data is durable.</li>
 * </ol>
 * Building and resolution run <em>outside</em> any transaction (no consistency boundary is needed for
 * pure construction and a read).
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class CreatePlayerUseCase implements CreatePlayerInputPort {

    CreatePlayerPresenterOutputPort presenter;
    PlayerOperationsOutputPort playerOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    SceneRepositoryOperationsOutputPort sceneOps;
    TransactionOperationsOutputPort txOps;

    @Override
    public void createPlayer(String startingSceneId) {
        try {
            // Initiating actor: the system at startup — no security assertion is required.

            // Checkpoint 1 — construct the value objects and aggregate (validity gate). The acting
            // player's id is ambient (pulled from playerOps); the starting scene is the authored carrier.
            PlayerId playerId;
            SceneId startingScene;
            Player player;
            try {
                playerId = new PlayerId(playerOps.currentPlayerId());
                startingScene = new SceneId(startingSceneId);
                player = Player.builder().id(playerId).currentScene(startingScene).build();
            } catch (IllegalArgumentException | NullPointerException e) {
                presenter.presentInvalidParametersError(e);
                return;
            }

            // Checkpoint 2 — inter-aggregate rule: the starting scene must resolve to a known scene.
            // A read, so it runs outside the transaction.
            if (sceneOps.findScene(startingScene).isEmpty()) {
                presenter.presentStartingSceneUnknown(startingScene);
                return;
            }

            // Checkpoint 3 — create once, atomically. The existence guard lives inside the same
            // transaction as the write so the create-if-absent decision cannot interleave with a
            // concurrent creation; present only after the transaction commits.
            txOps.doInTransaction(false, () -> {
                if (playerRepositoryOps.findPlayer(playerId).isPresent()) {
                    txOps.doAfterCommit(() -> presenter.presentPlayerAlreadyExists(playerId));
                    return;
                }
                playerRepositoryOps.savePlayer(player);
                txOps.doAfterCommit(() -> presenter.presentSuccessfulPlayerCreation(player));
            });

        } catch (Exception e) {
            // Outermost checkpoint. Anything not handled above ends here — notably a
            // PersistenceOperationsError from findScene/findPlayer/savePlayer, which has also rolled the
            // transaction back before propagating out of doInTransaction.
            presenter.presentError(e);
        }
    }
}
