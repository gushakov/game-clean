package com.github.gameclean.core.usecase.orient;

import com.github.gameclean.core.model.player.Player;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.port.persistence.PlayerRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.persistence.SceneRepositoryOperationsOutputPort;
import com.github.gameclean.core.port.player.PlayerOperationsOutputPort;
import com.github.gameclean.core.port.SubcaseAlreadyPresented;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.Optional;

/**
 * Shared <b>guarded-prologue subcase</b>: resolves the acting player and the scene they currently stand in
 * — the opening every interaction grounded in the player's location performs ({@code look}, {@code move},
 * and later {@code examine}, {@code take}, ...). Framework-free, constructed by the composition root and
 * handed the <em>same presenter instance</em> as the parent use case it serves.
 *
 * <p>It fuses the two subcase paths per outcome branch, and so honours "presentation is terminal" at the
 * granularity of the whole interaction:
 * <ul>
 *   <li><b>Failure branches behave as a terminal subcase</b> — a missing player or a dangling
 *       current-scene reference is <em>presented</em> through the shared presenter, then signalled by
 *       throwing {@link SubcaseAlreadyPresented} so the parent's checkpoint ends as a no-op.</li>
 *   <li><b>The success branch behaves as a helper</b> — it presents nothing and <em>returns</em> an
 *       {@link OrientPlayerResult} (the player and their current scene) for the parent to act on.</li>
 * </ul>
 * Each invocation therefore either presents-and-throws or returns-without-presenting — never both — so the
 * parent presents exactly once on every path. Unexpected failures (a malformed configured id, a
 * persistence fault) are simply propagated to the parent's outermost {@code catch}.
 */
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class OrientPlayerSubcase implements OrientPlayerSubcaseInputPort {

    OrientPlayerPresenterOutputPort presenter;
    PlayerOperationsOutputPort playerOps;
    PlayerRepositoryOperationsOutputPort playerRepositoryOps;
    SceneRepositoryOperationsOutputPort sceneOps;

    @Override
    public OrientPlayerResult playerGetsBearings() {

        // Initiating actor: the player — ambient, resolved here rather than passed in. Constructing the id
        // value object is the validity gate; a malformed id throws and propagates to the parent.
        PlayerId id = new PlayerId(playerOps.currentPlayerId());

        // Read the player and resolve where they stand. Reads run outside any transaction.
        Optional<Player> player = playerRepositoryOps.findPlayer(id);
        if (player.isEmpty()) {
            presenter.presentPlayerNotFound(id);
            throw new SubcaseAlreadyPresented();
        }

        SceneId currentSceneId = player.get().getCurrentScene();
        Optional<Scene> currentScene = sceneOps.findScene(currentSceneId);
        if (currentScene.isEmpty()) {
            presenter.presentCurrentSceneNotFound(currentSceneId);
            throw new SubcaseAlreadyPresented();
        }

        // Success: return the oriented player and scene to the caller, presenting nothing.
        return new OrientPlayerResult(player.get(), currentScene.get());
    }
}
