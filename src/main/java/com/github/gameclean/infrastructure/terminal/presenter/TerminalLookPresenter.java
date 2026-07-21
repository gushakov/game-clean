package com.github.gameclean.infrastructure.terminal.presenter;

import com.github.gameclean.core.model.item.Item;
import com.github.gameclean.core.model.npc.Npc;
import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.Scene;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.explore.LookPresenterOutputPort;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import com.github.gameclean.infrastructure.terminal.render.Console;
import com.github.gameclean.infrastructure.terminal.render.CurrentSceneRenderer;
import com.github.gameclean.infrastructure.terminal.render.OrientRenderer;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * Secondary (driven) adapter rendering the {@code Look} use case's outcomes to the shared JLine console. It
 * implements the two flat presenter ports the interaction's artifacts drive — the
 * {@link OrientPlayerPresenterOutputPort orient subcase's} not-founds (delegated to the shared
 * {@link OrientRenderer}) and {@code look}'s own port, whose success delegates to the shared
 * {@link CurrentSceneRenderer}; only the {@code presentError} catch-all carries a use-case-specific log tag.
 * {@link TerminalMovePresenter} is its symmetric peer, delegating to the same renderers.
 *
 * <p>It is a distinct bean from the input loop (opposite direction of the hexagon) and from the move
 * presenter; what they share is the {@code Console}/{@code CurrentSceneRenderer} <em>resources</em>, not
 * the adapter itself.
 */
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Slf4j
public class TerminalLookPresenter implements OrientPlayerPresenterOutputPort, LookPresenterOutputPort {

    OrientRenderer orientRenderer;
    CurrentSceneRenderer sceneRenderer;
    Console console;

    @Override
    public void presentScene(Scene scene, List<Item> itemsOnGround, List<Npc> npcsPresent) {
        sceneRenderer.renderScene(scene, itemsOnGround, npcsPresent);
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
    public void presentError(Exception e) {
        log.error("[Look] Unexpected error", e);
        console.printError("Something went wrong. Please try again.");
    }
}
