package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.explore.LookPresenterOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Secondary (driven) adapter rendering the {@code Look} use case's outcomes to the shared JLine console.
 * Its whole surface is the {@link com.github.gameclean.core.usecase.explore.CurrentScenePresenterOutputPort}
 * cluster, so it delegates every render to the shared {@link CurrentSceneRenderer}; only the
 * {@code presentError} catch-all carries a use-case-specific log tag. {@link TerminalMovePresenter} is its
 * symmetric peer, delegating the same cluster to the same renderer.
 *
 * <p>It is a distinct bean from the input loop (opposite direction of the hexagon) and from the move
 * presenter; what they share is the {@code Console}/{@code CurrentSceneRenderer} <em>resources</em>, not
 * the adapter itself.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TerminalLookPresenter implements LookPresenterOutputPort {

    private final CurrentSceneRenderer sceneRenderer;
    private final Console console;

    @Override
    public void presentScene(Scene scene) {
        sceneRenderer.renderScene(scene);
    }

    @Override
    public void presentPlayerNotFound(PlayerId playerId) {
        sceneRenderer.renderPlayerNotFound(playerId);
    }

    @Override
    public void presentCurrentSceneNotFound(SceneId sceneId) {
        sceneRenderer.renderCurrentSceneNotFound(sceneId);
    }

    @Override
    public void presentError(Exception e) {
        log.error("[Look] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
