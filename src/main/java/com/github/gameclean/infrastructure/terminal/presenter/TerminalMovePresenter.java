package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.explore.MovePresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.CurrentSceneRenderer;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Secondary (driven) adapter rendering the {@code Move} use case's outcomes to the shared JLine console —
 * the symmetric peer of {@link TerminalLookPresenter}. The three current-scene-cluster outcomes it shares
 * with {@code look} (including the entered scene on success) delegate to the shared
 * {@link CurrentSceneRenderer}; the two outcomes peculiar to moving — no such exit, a dangling target —
 * are rendered here through the {@link Console}.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalMovePresenter implements MovePresenterOutputPort {

    OrientRenderer orientRenderer;
    CurrentSceneRenderer sceneRenderer;
    Console console;

    @Override
    public void presentScene(Scene scene, List<Item> itemsOnGround) {
        sceneRenderer.renderScene(scene, itemsOnGround);
    }

    @Override
    public void presentPlayerNotFound(PlayerId playerId) {
        orientRenderer.renderPlayerNotFound(playerId);
    }

    @Override
    public void presentCurrentSceneNotFound(SceneId sceneId) {
        orientRenderer.renderCurrentSceneNotFound(sceneId);
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
