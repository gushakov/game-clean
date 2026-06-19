package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.explore.LookPresenterOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.CurrentSceneRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Secondary (driven) adapter rendering the {@code Look} use case's outcomes to the shared JLine console.
 * Its whole surface is the {@link OrientPlayerPresenterOutputPort}
 * cluster, so it delegates every render to the shared {@link CurrentSceneRenderer}; only the
 * {@code presentError} catch-all carries a use-case-specific log tag. {@link TerminalMovePresenter} is its
 * symmetric peer, delegating the same cluster to the same renderer.
 *
 * <p>It is a distinct bean from the input loop (opposite direction of the hexagon) and from the move
 * presenter; what they share is the {@code Console}/{@code CurrentSceneRenderer} <em>resources</em>, not
 * the adapter itself.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalLookPresenter implements LookPresenterOutputPort {

    CurrentSceneRenderer sceneRenderer;
    Console console;

    @Override
    public void presentScene(Scene scene, List<Item> itemsOnGround) {
        sceneRenderer.renderScene(scene, itemsOnGround);
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
