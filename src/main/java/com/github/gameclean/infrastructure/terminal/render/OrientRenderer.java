package com.github.gameclean.infrastructure.terminal.render;

import com.github.gameclean.core.model.player.PlayerId;
import com.github.gameclean.core.model.scene.SceneId;
import com.github.gameclean.core.usecase.orient.OrientPlayerPresenterOutputPort;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Renders the two not-found outcomes of the {@link OrientPlayerPresenterOutputPort orient} cluster — no acting
 * player, dangling current scene — to the shared console. It is the single home of that rendering, injected by
 * every presenter whose use case opens with the {@code orient} subcase ({@code look}, {@code move},
 * {@code examine}), so the two messages are written one way rather than copied per use case.
 *
 * <p>Extracted from {@code CurrentSceneRenderer} when {@code examine} arrived: examine shares the orient
 * <em>opening</em> (and so these outcomes) but not the scene-rendering ending, mirroring the presenter-port
 * re-split — a shared <em>collaborator</em> (composition), domain-aware (it knows {@link PlayerId}/{@link
 * SceneId}) over the domain-agnostic {@link Console}.
 */
@Component
@ConditionalOnProperty(prefix = "game.terminal", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrientRenderer {

    private final Console console;

    /** The acting player does not exist — a configuration or data fault, surfaced plainly. */
    public void renderPlayerNotFound(PlayerId playerId) {
        console.printError("There is no player '%s'.".formatted(playerId.getValue()));
    }

    /** The player's recorded current scene resolves to nothing. */
    public void renderCurrentSceneNotFound(SceneId sceneId) {
        console.printError("You seem to be nowhere — scene '%s' does not exist.".formatted(sceneId.getValue()));
    }
}
