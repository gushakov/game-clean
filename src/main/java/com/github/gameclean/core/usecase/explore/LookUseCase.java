package com.github.gameclean.core.usecase.explore;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Optional;

/**
 * Describes the player's current surroundings. Implementation of {@link LookInputPort};
 * framework-free, wired by the composition root, exercised in isolation against mocked ports.
 *
 * <p>This is the project's first <b>read-only</b> use case, and it shows what that costs: nothing.
 * The interaction is two reads and a presentation, with <em>no transaction at all</em> — reads run
 * outside any consistency boundary (there is nothing to commit, so there is no {@code doInTransaction}
 * and no after-commit hook). Construction of the {@link PlayerId} value object happens here, the
 * single validity gate; the controller carries only the primitive id.
 *
 * <p>Outcomes are reached by <em>branch-and-present</em>, not by throwing: a missing player or a
 * dangling current-scene reference each calls a dedicated presenter method. The outermost checkpoint
 * routes anything unexpected (a malformed configured id, a persistence fault) to {@code presentError}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class LookUseCase implements LookInputPort {

    LookPresenterOutputPort presenter;
    PlayerRepositoryOperationsOutputPort playerOps;
    SceneRepositoryOperationsOutputPort sceneOps;

    @Override
    public void look(String playerId) {
        try {
            // Initiating actor: the player. Construct the id value object — the validity gate.
            PlayerId id = new PlayerId(playerId);

            // Read the player to find where it is. Read-only: outside any transaction.
            Optional<Player> player = playerOps.findPlayer(id);
            if (player.isEmpty()) {
                presenter.presentPlayerNotFound(id);
                return;
            }

            // Resolve the current scene — an inter-aggregate reference that may dangle.
            SceneId currentScene = player.get().getCurrentScene();
            Optional<Scene> scene = sceneOps.findScene(currentScene);
            if (scene.isEmpty()) {
                presenter.presentCurrentSceneNotFound(currentScene);
                return;
            }

            presenter.presentScene(scene.get());

        } catch (Exception e) {
            // Outermost checkpoint: a malformed configured id or a PersistenceOperationsError ends here.
            presenter.presentError(e);
        }
    }
}
