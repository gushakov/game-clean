package com.github.gameclean.infrastructure.terminal;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.explore.MovePresenterOutputPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Secondary (driven) adapter rendering the {@code Move} use case's outcomes to the shared JLine console —
 * the symmetric peer of {@link TerminalLookPresenter}. The three current-scene-cluster outcomes it shares
 * with {@code look} (including the entered scene on success) delegate to the shared
 * {@link CurrentSceneRenderer}; the two outcomes peculiar to moving — no such exit, a dangling target —
 * are rendered here through the {@link Console}.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TerminalMovePresenter implements MovePresenterOutputPort {

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
    public void presentNoSuchExit(String exitName) {
        console.printError("There is no exit '%s' from here.".formatted(exitName));
    }

    @Override
    public void presentTargetSceneNotFound(SceneId target) {
        console.printError("That way leads nowhere — scene '%s' does not exist.".formatted(target.getValue()));
    }

    @Override
    public void presentError(Exception e) {
        log.error("[Move] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
